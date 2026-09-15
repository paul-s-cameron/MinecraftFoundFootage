package com.sp.entity.custom;

import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.cca_stuff.SmilerComponent;
import com.sp.init.BackroomsLevels;
import com.sp.world.levels.BackroomsLevelWithLights;
import com.sp.world.levels.custom.Level1BackroomsLevel;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ai.TargetPredicate;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.World;

import java.util.List;

public class SmilerEntity extends MobEntity {
    /**
     * Long enough for the client's fade-out to finish. It runs over 30 ticks, so the old value of
     * 20 removed the entity at about a third opacity — it popped instead of fading.
     */
    private static final int FADE_OUT_TICKS = 30;

    private final SmilerComponent component;
    private int finalTicks;

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

    @Override
    protected void initGoals() {
        this.targetSelector.add(1, new ActiveTargetGoal<>(this, PlayerEntity.class, 0, true, false, null));
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
            }
        }


        super.tick();
    }

    private boolean inBlackout() {
        return this.getWorld().getRegistryKey() == BackroomsLevels.LEVEL1_WORLD_KEY
                && BackroomsLevels.getLevel(this.getWorld()).orElse(null) instanceof Level1BackroomsLevel level
                && level.getLightState() == BackroomsLevelWithLights.LightState.BLACKOUT;
    }

    //From Enderman. Don't need anything too fancy
    private boolean isPlayerStaring(PlayerEntity player) {
        Vec3d vec3d = player.getRotationVec(1.0F).normalize();
        Vec3d vec3d2 = new Vec3d(this.getX() - player.getX(), this.getEyeY() - player.getEyeY(), this.getZ() - player.getZ());
        double d = vec3d2.length();
        vec3d2 = vec3d2.normalize();
        double e = vec3d.dotProduct(vec3d2);
        return e > 1.0 - 0.35 / d && player.canSee(this);
    }

    public static DefaultAttributeContainer.Builder createSmilerAttributes(){
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 1000)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, 1000);
    }


}
