package com.sp.entity.custom;

import com.sp.SPBRevamped;
import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.cca_stuff.SmilerComponent;
import com.sp.compat.hardcorerevival.Revival;
import com.sp.init.BackroomsLevels;
import com.sp.init.ModDamageTypes;
import com.sp.world.levels.BackroomsLevelWithLights;
import com.sp.world.levels.custom.Level1BackroomsLevel;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * It picks a player when it arrives and that player is its business for the rest of its life.
 *
 * <p>Three states, and no hidden numbers — an aggression meter was tried and thrown away, because
 * it made the creature's condition invisible and a player could not tell how much trouble they
 * were in. It <b>watches</b> by default, standing still and doing nothing. It <b>stalks</b> while
 * its player is lit or noisy, creeping slower than a walk. And within striking distance of a stalk
 * it takes them down in one hit.
 *
 * <p>A watching smiler never strikes. That is what makes it possible to sneak past one, and it is
 * the difference between a thing in the way and a wall. The way out is the lore's own and all
 * three parts are required: kill the light, crouch, and say nothing.
 */
public class SmilerEntity extends MobEntity {
    /**
     * Long enough for the client's fade-out to finish. It runs over 30 ticks, so the old value of
     * 20 removed the entity at about a third opacity — it popped instead of fading.
     */
    private static final int FADE_OUT_TICKS = 30;

    /** How far away a lit flashlight still calls it. */
    private static final double AWARENESS_RANGE = 48.0;
    /** Talking carries, but not across a level. */
    private static final double VOICE_RANGE = 16.0;
    /** Footsteps are the quietest of the tells, so walking upright only matters close by. */
    private static final double FOOTSTEP_RANGE = 8.0;
    /** Close enough to take someone. Reached while stalking, this is immediate. */
    private static final double STRIKE_RANGE = 5.0;

    /** Slower than a walk: standing still or being cornered is what kills you, not the chase. */
    private static final double CREEP_SPEED = 0.55;
    /** Repathing every tick is wasted work; nobody outmanoeuvres a creep in half a second. */
    private static final int REPATH_INTERVAL_TICKS = 10;

    /** Comfortably past any health pool. Hardcore Revival turns the would-be kill into a downing. */
    private static final float STRIKE_DAMAGE = 1000.0f;
    /** Guards against striking twice before the knockout has registered. */
    private static final int STRIKE_COOLDOWN_TICKS = 20;
    /** Hitting one makes it yours for a while, whatever your light and your feet are doing. */
    private static final int PROVOKED_TICKS = 200;

    /** How far a player must move in a tick to be making noise with it. */
    private static final double FOOTSTEP_MOVEMENT = 0.01;

    /** A smiler that cannot path says so a few times and then stops filling the log. */
    private static final int MAX_PATH_COMPLAINTS = 3;

    private final SmilerComponent component;
    private int finalTicks;

    /** Chosen on arrival and kept until they stop being available. */
    @Nullable
    private UUID locked;
    private int provokedFor;
    private int repathIn;
    private int strikeCooldown;
    /** Whether the last attempt to path actually took; false means creep in a straight line. */
    private boolean pathing;
    private int pathComplaints;
    private boolean announcedStalk;

    public SmilerEntity(EntityType<? extends MobEntity> entityType, World world) {
        super(entityType, world);
        this.component = InitializeComponents.SMILER.get(this);

        if(!world.isClient){
            Random random = Random.create();
            this.component.setRandomTexture(random.nextBetween(1,3));
            this.component.sync();
        }
        this.finalTicks = FADE_OUT_TICKS;
    }

    /**
     * A smiler exists only for the blackout that made it, so it has no business surviving a save.
     * Without this one left in an unloaded chunk would come back later, in a lit level, and only
     * be cleaned up once something ticked it.
     */
    @Override
    public boolean shouldSave() {
        return false;
    }

    /**
     * Vanilla would despawn one for being far from any player. Nothing may remove a smiler except
     * the lights returning — a player walking away and coming back should find it still standing.
     */
    @Override
    public void checkDespawn() {
    }

    @Override
    public void tick() {
        if(!this.getWorld().isClient) {
            // The lights coming back is the only thing that dispels a smiler, and the only vanish
            // a player is ever meant to witness — light destroys them, which is the creature's own
            // rule, so seeing it happen is the point rather than a seam. Nothing expires them and
            // nothing retires them quietly: one that arrives is still standing at the end.
            if (!this.component.shouldDisappear() && !this.inBlackout()) {
                this.component.setShouldDisappear(true);
                this.component.sync();
            }

            if(this.component.shouldDisappear()) {
                this.finalTicks--;
                if(this.finalTicks <= 0){
                    this.discard();
                }
            } else {
                this.hunt();
            }
        }

        super.tick();
    }

    private void hunt() {
        if (this.provokedFor > 0) {
            this.provokedFor--;
        }
        if (this.strikeCooldown > 0) {
            this.strikeCooldown--;
        }
        if (this.repathIn > 0) {
            this.repathIn--;
        }

        PlayerEntity target = this.lockedTarget();
        if (target == null) {
            this.getNavigation().stop();
            return;
        }

        if (!this.isStalking(target)) {
            // Watching. It stands exactly where it is and does nothing at all, which is what lets
            // a dark and silent player walk past one rather than only away from it.
            this.getNavigation().stop();
            return;
        }

        if (!this.announcedStalk) {
            this.announcedStalk = true;
            SPBRevamped.LOGGER.info("Smiler locked on {} and started stalking.", target.getEntityName());
        }

        this.getLookControl().lookAt(target, 30.0f, 30.0f);

        if (this.squaredDistanceTo(target) <= STRIKE_RANGE * STRIKE_RANGE) {
            this.strike(target);
            return;
        }

        if (this.repathIn <= 0) {
            this.repathIn = REPATH_INTERVAL_TICKS;
            this.pathing = this.getNavigation().startMovingTo(target, CREEP_SPEED);

            if (!this.pathing && this.pathComplaints < MAX_PATH_COMPLAINTS) {
                this.pathComplaints++;
                BlockPos below = this.getBlockPos().down();
                SPBRevamped.LOGGER.warn("Smiler at {} could not path to {} ({} blocks away)."
                                + " onGround={} noGravity={} noClip={} velocityY={} fallDistance={}"
                                + " standingOn={}",
                        this.getBlockPos().toShortString(), target.getEntityName(),
                        Math.round(this.distanceTo(target)), this.isOnGround(), this.hasNoGravity(),
                        this.noClip, String.format("%.4f", this.getVelocity().y), this.fallDistance,
                        this.getWorld().getBlockState(below));
            }
        }

        if (!this.pathing) {
            this.creepDirectlyAt(target);
        }
    }

    /**
     * Walks straight at the player without pathfinding, and without caring whether the game thinks
     * this thing is standing on anything.
     *
     * <p>Two separate problems make the ordinary route unreliable here. Pathfinding refuses
     * outright unless {@code isOnGround}, and a mob the game believes is airborne is also moved by
     * {@code travel} with air control rather than ground friction — a fraction of the intended
     * speed. Both produce the same symptom of a creature that will not come for you.
     *
     * <p>Setting horizontal velocity directly each tick sidesteps both: the speed is what the
     * attribute says it is either way, gravity still owns the vertical, and walls still stop it.
     * What it gives up is going around corners, which is why a path is still preferred when the
     * navigator will give us one.
     */
    private void creepDirectlyAt(PlayerEntity target) {
        Vec3d toTarget = target.getPos().subtract(this.getPos());
        Vec3d flat = new Vec3d(toTarget.x, 0.0, toTarget.z);
        if (flat.lengthSquared() < 1.0E-4) {
            return;
        }

        Vec3d step = flat.normalize()
                .multiply(this.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED) * CREEP_SPEED);
        this.setVelocity(step.x, this.getVelocity().y, step.z);
        this.velocityDirty = true;
    }

    /**
     * No wind-up and no second chance. By the time it is this close the player has already had the
     * entire approach to react, and the approach is the warning.
     */
    private void strike(PlayerEntity target) {
        this.getNavigation().stop();
        if (this.strikeCooldown > 0) {
            return;
        }
        this.strikeCooldown = STRIKE_COOLDOWN_TICKS;
        // The mod's own smiler damage, which carries its death message and is in bypasses_armor so
        // a one-hit downing cannot be turned into a two-hit one by whatever a player is wearing.
        // Deliberately *not* in bypasses_knockout: Hardcore Revival turns what would be a kill into
        // a downed player somebody has to come back for.
        target.damage(ModDamageTypes.of(this.getWorld(), ModDamageTypes.SMILER), STRIKE_DAMAGE);
    }

    /** Lit, loud, or recently hit. Any one of them is enough; none of them and it stops. */
    private boolean isStalking(PlayerEntity target) {
        if (this.provokedFor > 0) {
            return true;
        }

        PlayerComponent component = InitializeComponents.PLAYER.get(target);
        double distanceSquared = this.squaredDistanceTo(target);

        if (component.isFlashLightOn() && distanceSquared <= AWARENESS_RANGE * AWARENESS_RANGE) {
            return true;
        }
        if (target.isSprinting()) {
            return true;
        }
        if (component.isSpeaking() && distanceSquared <= VOICE_RANGE * VOICE_RANGE) {
            return true;
        }
        // Footsteps: walking upright nearby. Standing still is not noise, and crouching is silent,
        // which is what makes "crouch away" the answer rather than "walk away".
        return !target.isSneaking()
                && distanceSquared <= FOOTSTEP_RANGE * FOOTSTEP_RANGE
                && this.isMoving(target);
    }

    private boolean isMoving(PlayerEntity player) {
        double dx = player.getX() - player.prevX;
        double dz = player.getZ() - player.prevZ;
        return dx * dx + dz * dz > FOOTSTEP_MOVEMENT * FOOTSTEP_MOVEMENT;
    }

    /** Hitting one takes it personally, whatever your light and your feet were doing. */
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!this.getWorld().isClient && source.getAttacker() instanceof PlayerEntity attacker
                && this.isVictim(attacker)) {
            this.locked = attacker.getUuid();
            this.provokedFor = PROVOKED_TICKS;
        }
        return super.damage(source, amount);
    }

    /**
     * The player this one arrived for. Only replaced when they stop being available — downed,
     * dead, a ghost, or gone — at which point it simply moves on to whoever is nearest.
     */
    @Nullable
    private PlayerEntity lockedTarget() {
        if (this.locked != null) {
            PlayerEntity held = this.getWorld().getPlayerByUuid(this.locked);
            if (this.isVictim(held)) {
                return held;
            }
        }

        PlayerEntity nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (PlayerEntity candidate : this.getWorld().getPlayers()) {
            if (!this.isVictim(candidate)) {
                continue;
            }
            double distance = this.squaredDistanceTo(candidate);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = candidate;
            }
        }

        this.locked = nearest == null ? null : nearest.getUuid();
        this.provokedFor = 0;
        return nearest;
    }

    /**
     * Somebody worth taking. Ghosts and skinwalker captives are spectators, and a player already
     * downed has been taken — finishing them would turn one mistake into a death.
     */
    private boolean isVictim(@Nullable PlayerEntity player) {
        return player != null
                && player.isAlive()
                && !player.isSpectator()
                && player.getWorld() == this.getWorld()
                && !Revival.isDowned(player);
    }

    private boolean inBlackout() {
        return this.getWorld().getRegistryKey() == BackroomsLevels.LEVEL1_WORLD_KEY
                && BackroomsLevels.getLevel(this.getWorld()).orElse(null) instanceof Level1BackroomsLevel level
                && level.getLightState() == BackroomsLevelWithLights.LightState.BLACKOUT;
    }

    public static DefaultAttributeContainer.Builder createSmilerAttributes(){
        return MobEntity.createMobAttributes()
                // Not meant to be fought: a player who decides to attack one should find that it
                // simply does not work, and then regret having tried.
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 1000)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1000)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, AWARENESS_RANGE);
    }
}
