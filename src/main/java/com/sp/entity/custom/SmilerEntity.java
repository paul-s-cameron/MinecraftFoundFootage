package com.sp.entity.custom;

import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.cca_stuff.SmilerComponent;
import com.sp.compat.hardcorerevival.Revival;
import com.sp.entity.SmilerSpawner;
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
import net.minecraft.server.world.ServerWorld;
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

    /**
     * Navigation speeds are a multiplier on the movement-speed attribute, and the pair below was
     * calibrated against measurement rather than arithmetic: a smiler logged 1.02 blocks/sec at
     * CREEP_SPEED 0.55 against an attribute of 0.28. The attribute is now 0.5 so the rush needs
     * no absurd multiplier, and the creep multiplier is scaled down by the same factor - the
     * product is unchanged, so the creep still walks at that measured 1.02 blocks/sec.
     */
    private static final double CREEP_SPEED = 0.308;
    /**
     * The rush, in the open, once it has been seen. A player walks 4.32 blocks/sec and sprints
     * 5.61; this works out to about 6.1, so sprinting away buys time and distance but never an
     * escape. Turning the light off and crouching away still does, which is the point - flight is
     * meant to be the losing answer.
     */
    private static final double RUSH_SPEED = 1.84;
    /** Close enough that the creep is over and it simply comes at you. */
    private static final double RUSH_RANGE = 9.0;

    /**
     * A smiler does nothing to a player who has never laid eyes on it. Relocation only ever lands
     * one where it cannot be seen, so without this gate the rush always began behind the player
     * and the first thing they knew of the creature was the downing - which is not a scare, it is
     * an unexplained death. Being looked at once is the price of admission; after that it is free
     * to act, and it never needs looking at again.
     */
    private static final double ACKNOWLEDGE_RANGE = 32.0;
    /**
     * How near the middle of the screen it has to fall to count as looked at. 0.75 is about 41
     * degrees off centre, so anywhere comfortably on screen rather than only dead ahead.
     */
    private static final double ACKNOWLEDGE_DOT = 0.75;

    /** Repathing every tick is wasted work; nobody outmanoeuvres a creep in half a second. */
    private static final int REPATH_INTERVAL_TICKS = 10;

    /**
     * A smiler walks slower than a crouching player, so its walk can never close a gap - and it is
     * not supposed to. What costs you ground is looking away: while nobody can see it, it gains
     * this much in one unseen step. Reappearing out of sight both before and after is what makes it
     * read as the thing having moved on its own rather than as a teleport.
     */
    private static final double RELOCATE_STEP = 7.0;
    /** How often an unseen step is allowed, so it gains ground in beats rather than a glide. */
    private static final int RELOCATE_INTERVAL_TICKS = 30;
    /**
     * Where an unseen step hands over to the rush. This has to sit inside RUSH_RANGE or the smiler
     * arrives at a distance only the creep can close, and the creep is slower than walking - which
     * makes the strike unreachable however many times it steps. A floor above RUSH_RANGE is the
     * bug this constant exists to prevent.
     */
    private static final double RELOCATE_MIN_DISTANCE = STRIKE_RANGE + 3.0;
    /** Sideways spread, so it does not file in along one straight line. */
    private static final float RELOCATE_SPREAD_DEGREES = 50.0f;
    private static final int RELOCATE_ATTEMPTS = 8;

    /** Comfortably past any health pool. Hardcore Revival turns the would-be kill into a downing. */
    private static final float STRIKE_DAMAGE = 1000.0f;
    /** Guards against striking twice before the knockout has registered. */
    private static final int STRIKE_COOLDOWN_TICKS = 20;
    /** Hitting one makes it yours for a while, whatever your light and your feet are doing. */
    private static final int PROVOKED_TICKS = 200;

    /** How far a player must move in a tick to be making noise with it. */
    private static final double FOOTSTEP_MOVEMENT = 0.01;

    static {
        // The mistake that shipped twice, made loud and made early. These three distances are not
        // independent: the unseen step hands over to the rush, so its floor has to sit between the
        // strike and the rush. Above RUSH_RANGE, the gap it leaves can only be closed by a creep
        // slower than a walking player and the strike is unreachable however many steps it takes -
        // which is exactly what happened, once as a floor of 7 against a strike range of 5, and
        // once as no relocation at all. At or below STRIKE_RANGE the step delivers the kill itself,
        // out of sight, and the player never sees what hit them.
        //
        // This throws at class load, so the dedicated-server boot in CLAUDE.md catches it. A tuning
        // constant can only be wrong here because somebody edited it, never because of anything a
        // player did, so failing loudly is right.
        if (RELOCATE_MIN_DISTANCE <= STRIKE_RANGE || RELOCATE_MIN_DISTANCE >= RUSH_RANGE) {
            throw new IllegalStateException(String.format(
                    "Smiler distances must satisfy STRIKE_RANGE < RELOCATE_MIN_DISTANCE < RUSH_RANGE,"
                            + " got %.1f < %.1f < %.1f",
                    STRIKE_RANGE, RELOCATE_MIN_DISTANCE, RUSH_RANGE));
        }
        // Measured, not derived: 1.02 blocks/sec at a CREEP_SPEED of 0.55 against an attribute of
        // 0.28. The rush only means anything if it outruns a sprint, and it cannot do that while it
        // is the slower of the two multipliers.
        if (RUSH_SPEED <= CREEP_SPEED) {
            throw new IllegalStateException(String.format(
                    "Smiler RUSH_SPEED (%.3f) must exceed CREEP_SPEED (%.3f)",
                    RUSH_SPEED, CREEP_SPEED));
        }
    }


    private final SmilerComponent component;
    private int finalTicks;

    /** Chosen on arrival and kept until they stop being available. */
    @Nullable
    private UUID locked;
    private int provokedFor;
    private int repathIn;
    private int relocateIn;
    /** Whether the last path was asked for at rushing speed. */
    private boolean rushing;
    /** Whether the locked player has ever actually seen this smiler. */
    private boolean acknowledged;
    private int strikeCooldown;
    /** Whether the last attempt to path actually took; false means creep in a straight line. */
    private boolean pathing;

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
        if (this.relocateIn > 0) {
            this.relocateIn--;
        }

        PlayerEntity target = this.lockedTarget();
        if (target == null) {
            this.getNavigation().stop();
            return;
        }

        if (!this.acknowledged && this.hasBeenSeenBy(target)) {
            this.acknowledged = true;
        }

        if (!this.isStalking(target)) {
            // Watching. It stands exactly where it is and does nothing at all, which is what lets
            // a dark and silent player walk past one rather than only away from it.
            this.getNavigation().stop();
            return;
        }

        this.getLookControl().lookAt(target, 30.0f, 30.0f);

        if (this.squaredDistanceTo(target) <= STRIKE_RANGE * STRIKE_RANGE) {
            this.strike(target);
            return;
        }

        if (this.relocateIn <= 0) {
            this.relocateIn = RELOCATE_INTERVAL_TICKS;
            this.tryRelocateCloser(target);
        }

        // Crossing into the rush must take effect now rather than whenever the repath timer
        // happens to come round, or the reveal is a smiler still creeping for half a second.
        boolean shouldRush = this.squaredDistanceTo(target) <= RUSH_RANGE * RUSH_RANGE;
        if (shouldRush != this.rushing) {
            this.rushing = shouldRush;
            this.repathIn = 0;
        }

        if (this.repathIn <= 0) {
            this.repathIn = REPATH_INTERVAL_TICKS;
            this.pathing = this.getNavigation()
                    .startMovingTo(target, this.rushing ? RUSH_SPEED : CREEP_SPEED);
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
    /**
     * One unseen step closer, or nothing. Every condition here is a promise to the player: it only
     * moves while unwatched, it only does so while provoked, and it never materialises on top of
     * you. Turn the light off and crouch away and it is never stalking, so it never takes a step -
     * the counterplay is not a reflex check, it is a decision.
     */
    /** On screen, near enough to make out, and with nothing in the way. */
    private boolean hasBeenSeenBy(PlayerEntity player) {
        Vec3d eyes = player.getEyePos();
        Vec3d toFace = this.getEyePos().subtract(eyes);
        double distance = toFace.length();
        if (distance < 1.0E-4 || distance > ACKNOWLEDGE_RANGE) {
            return false;
        }
        if (player.getRotationVec(1.0f).normalize().dotProduct(toFace.normalize())
                < ACKNOWLEDGE_DOT) {
            return false;
        }
        return player.canSee(this);
    }

    private boolean tryRelocateCloser(PlayerEntity target) {
        if (!(this.getWorld() instanceof ServerWorld world)) {
            return false;
        }

        Vec3d here = this.getPos();
        // Being looked at right now forbids it outright: the whole effect is that the move is the
        // one thing you never catch happening.
        if (SmilerSpawner.isObserved(world, here)) {
            return false;
        }

        double distance = here.distanceTo(target.getPos());
        if (distance <= RELOCATE_MIN_DISTANCE) {
            return false;
        }

        Vec3d toward = new Vec3d(target.getX() - here.x, 0.0, target.getZ() - here.z);
        if (toward.lengthSquared() < 1.0E-6) {
            return false;
        }
        toward = toward.normalize();
        double step = Math.min(RELOCATE_STEP, distance - RELOCATE_MIN_DISTANCE);

        for (int attempt = 0; attempt < RELOCATE_ATTEMPTS; attempt++) {
            // Straight at the target first, then progressively off to one side, so a blocked
            // direct line becomes an arrival from an angle rather than no arrival at all.
            float spread = attempt == 0
                    ? 0.0f
                    : (world.getRandom().nextFloat() * 2.0f - 1.0f) * RELOCATE_SPREAD_DEGREES;
            Vec3d offset = toward.rotateY((float) Math.toRadians(spread)).multiply(step);
            Vec3d candidate = new Vec3d(Math.floor(here.x + offset.x) + 0.5, here.y,
                    Math.floor(here.z + offset.z) + 0.5);

            if (!SmilerSpawner.isStandable(world, candidate)
                    || SmilerSpawner.isObserved(world, candidate)) {
                continue;
            }

            this.getNavigation().stop();
            this.refreshPositionAndAngles(candidate.x, candidate.y, candidate.z,
                    this.getYaw(), this.getPitch());
            this.getLookControl().lookAt(target, 30.0f, 30.0f);
            return true;
        }

        return false;
    }

    private void creepDirectlyAt(PlayerEntity target) {
        Vec3d toTarget = target.getPos().subtract(this.getPos());
        Vec3d flat = new Vec3d(toTarget.x, 0.0, toTarget.z);
        if (flat.lengthSquared() < 1.0E-4) {
            return;
        }

        Vec3d step = flat.normalize()
                .multiply(this.getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED)
                        * (this.rushing ? RUSH_SPEED : CREEP_SPEED));
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
        // Ahead of being seen there is no stalking at all, and since the strike, the unseen step
        // and the rush all sit behind this one check, an unacknowledged smiler simply stands in
        // the dark and watches - which is the only thing a player who has not found it yet can
        // fairly be subjected to.
        if (!this.acknowledged) {
            return false;
        }

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
            // Whoever landed a hit has plainly found it, whatever the view cone says.
            this.acknowledged = true;
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

        // A different player inherits none of the last one's acquaintance with it.
        UUID previous = this.locked;
        this.locked = nearest == null ? null : nearest.getUuid();
        if (!java.util.Objects.equals(previous, this.locked)) {
            this.acknowledged = false;
        }
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
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.5)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, AWARENESS_RANGE);
    }
}
