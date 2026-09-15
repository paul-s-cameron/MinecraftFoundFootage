package com.sp.entity.custom;

import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.cca_stuff.SmilerComponent;
import com.sp.init.BackroomsLevels;
import com.sp.world.levels.BackroomsLevelWithLights;
import com.sp.world.levels.custom.Level1BackroomsLevel;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

/**
 * Drawn to light, provoked by panic.
 *
 * <p>It wants whatever is lit. Standing still with a torch on is an invitation, and a group that
 * has gathered somewhere is several invitations in one place. But it only turns on you if you give
 * it a reason: running where it can see you, talking near it, or hitting it. Kill your lights, stay
 * quiet, and back away, and it will lose interest — which is the creature's own rule and the only
 * way past one.
 *
 * <p>Attacks <b>down</b> a player rather than killing them, like everything else that hunts here.
 * Nothing removes a smiler but the lights coming back.
 */
public class SmilerEntity extends MobEntity {
    /**
     * Long enough for the client's fade-out to finish. It runs over 30 ticks, so the old value of
     * 20 removed the entity at about a third opacity — it popped instead of fading.
     */
    private static final int FADE_OUT_TICKS = 30;

    /** How far a lit flashlight calls one from. */
    private static final double ATTRACTION_RANGE = 32.0;
    /** Being this close is itself a provocation: the lore's survival rule is to <i>move away</i>. */
    private static final double CROWDING_RANGE = 6.0;
    /** Close enough that a lit torch is not merely a beacon but a stare it can object to. */
    private static final double LIT_RANGE = 12.0;
    /** Talking only matters up close, so a group is not punished for speaking across a level. */
    private static final double VOICE_RANGE = 10.0;
    private static final double ATTACK_REACH = 2.5;
    private static final int ATTACK_COOLDOWN_TICKS = 20;
    /** Repathing every tick is wasted work; a player cannot outmanoeuvre it in half a second. */
    private static final int REPATH_INTERVAL_TICKS = 10;

    /** Slow and deliberate while it is only curious. */
    private static final double APPROACH_SPEED = 0.6;
    /** Faster once provoked, but still slower than a sprint — and sprinting is what provokes it. */
    private static final double CHARGE_SPEED = 1.0;

    private static final int AGGRESSION_MAX = 90;
    private static final int AGGRESSION_ATTACKS_AT = 60;
    /** Standing too close. On its own, three seconds before it moves. */
    private static final int AGGRESSION_FROM_CROWDING = 1;
    /** A torch held on it nearby. Doubles up with crowding, which is the walk-right-up case. */
    private static final int AGGRESSION_FROM_LIGHT = 1;
    private static final int AGGRESSION_FROM_SPRINTING = 3;
    /** Deliberately gentle: talking is what the group is for, and it should not be a death sentence. */
    private static final int AGGRESSION_FROM_SPEAKING = 1;
    private static final int AGGRESSION_FROM_BEING_STRUCK = 45;
    private static final int AGGRESSION_DECAY = 1;

    private final SmilerComponent component;
    private int finalTicks;

    private int aggression;
    private int repathIn;
    private int attackCooldown;

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
        if (this.attackCooldown > 0) {
            this.attackCooldown--;
        }
        if (this.repathIn > 0) {
            this.repathIn--;
        }

        PlayerEntity nearest = this.nearestVictim();
        if (nearest == null) {
            this.cool();
            this.getNavigation().stop();
            return;
        }

        // Provocation builds; decay only happens when nothing is provoking it at all. Netting the
        // two against each other made standing still next to one a permanent stalemate, which is
        // the opposite of a threat — the way out is to put distance and darkness between you.
        int provocation = this.provocationFrom(nearest);
        this.aggression = provocation > 0
                ? Math.min(AGGRESSION_MAX, this.aggression + provocation)
                : Math.max(0, this.aggression - AGGRESSION_DECAY);

        if (this.aggression >= AGGRESSION_ATTACKS_AT) {
            this.charge(nearest);
            return;
        }

        // Not provoked: it only moves toward light, and stands perfectly still without any. That
        // stillness is the standoff — present, watching, in the way, and survivable.
        PlayerEntity lit = this.nearestLitPlayer();
        if (lit == null) {
            this.getNavigation().stop();
            return;
        }
        this.moveToward(lit, APPROACH_SPEED);
    }

    private void charge(PlayerEntity target) {
        this.moveToward(target, CHARGE_SPEED);

        if (this.attackCooldown <= 0 && this.squaredDistanceTo(target) <= ATTACK_REACH * ATTACK_REACH) {
            this.attackCooldown = ATTACK_COOLDOWN_TICKS;
            // An ordinary mob attack on purpose: it is not in the bypasses_knockout tag, so
            // Hardcore Revival turns what would be a kill into a downed player somebody can reach.
            this.tryAttack(target);
        }
    }

    private void moveToward(PlayerEntity target, double speed) {
        this.getLookControl().lookAt(target, 30.0f, 30.0f);
        if (this.repathIn <= 0) {
            this.repathIn = REPATH_INTERVAL_TICKS;
            this.getNavigation().startMovingTo(target, speed);
        }
    }

    private int provocationFrom(PlayerEntity player) {
        double distanceSquared = this.squaredDistanceTo(player);
        PlayerComponent component = InitializeComponents.PLAYER.get(player);
        int gain = 0;

        // Simply being there. The lore's survival rule is to back away gradually, not to hold your
        // ground, so holding it has to cost something or there is no rule at all.
        if (distanceSquared <= CROWDING_RANGE * CROWDING_RANGE) {
            gain += AGGRESSION_FROM_CROWDING;
        }
        // Light draws it from across the level; near enough, it is also an affront. Walking up to
        // one with a torch on is both at once, which is the case that should never have been safe.
        if (distanceSquared <= LIT_RANGE * LIT_RANGE && component.isFlashLightOn()) {
            gain += AGGRESSION_FROM_LIGHT;
        }
        // Panic, which is the lore's own trigger: running where it can see you.
        if (player.isSprinting() && this.canSee(player)) {
            gain += AGGRESSION_FROM_SPRINTING;
        }
        if (distanceSquared <= VOICE_RANGE * VOICE_RANGE && component.isSpeaking()) {
            gain += AGGRESSION_FROM_SPEAKING;
        }

        return gain;
    }

    private void cool() {
        this.aggression = Math.max(0, this.aggression - AGGRESSION_DECAY);
    }

    /** Hitting one is the loudest thing a player can do. */
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (!this.getWorld().isClient && source.getAttacker() instanceof PlayerEntity) {
            this.aggression = Math.min(AGGRESSION_MAX, this.aggression + AGGRESSION_FROM_BEING_STRUCK);
        }
        return super.damage(source, amount);
    }

    private PlayerEntity nearestVictim() {
        PlayerEntity nearest = this.getWorld().getClosestPlayer(this, ATTRACTION_RANGE);
        return this.isVictim(nearest) ? nearest : null;
    }

    /**
     * The nearest player actually carrying a light. Light is what draws it; a dark player is not a
     * destination, however close they are.
     */
    private PlayerEntity nearestLitPlayer() {
        PlayerEntity nearest = null;
        double nearestDistance = ATTRACTION_RANGE * ATTRACTION_RANGE;

        for (PlayerEntity player : this.getWorld().getPlayers()) {
            if (!this.isVictim(player) || !InitializeComponents.PLAYER.get(player).isFlashLightOn()) {
                continue;
            }
            double distance = this.squaredDistanceTo(player);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = player;
            }
        }

        return nearest;
    }

    /**
     * Ghosts and skinwalker captives are spectators — nothing to hunt, and reaching for one would
     * send it wandering after somebody who is not really there.
     */
    private boolean isVictim(PlayerEntity player) {
        return player != null && player.isAlive() && !player.isSpectator()
                && player.getWorld() == this.getWorld();
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
                // Slower than a sprint even while charging, so running is survivable — but running
                // is also what provokes it, which is the trade the whole creature is built on.
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.28)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, 7.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, ATTRACTION_RANGE);
    }
}
