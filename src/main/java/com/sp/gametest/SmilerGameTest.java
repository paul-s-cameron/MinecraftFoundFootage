package com.sp.gametest;

import com.sp.entity.SmilerSpawner;
import com.sp.entity.custom.SmilerEntity;
import com.sp.init.ModEntities;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.block.Blocks;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/**
 * Asserts the parts of the smiler that can be checked without a person playing the game.
 *
 * <p>Everything the smiler does hangs off two predicates — can it stand here, and can anyone see
 * here — so those are what is worth pinning down. Both were written by hand, both are geometry, and
 * a wrong answer from either is invisible in play: a smiler that never appears looks like a smiler
 * that has not spawned yet.
 *
 * <p>What is deliberately *not* here is the part that matters most, the approach and the strike.
 * {@code SmilerEntity.inBlackout()} requires the world to be Level 1 with its lights in BLACKOUT,
 * and a gametest world is neither, so a smiler spawned here starts fading immediately and lives
 * about thirty ticks. Testing the chase needs that condition made injectable first; see the note
 * in docs/threat-model.md.
 */
public class SmilerGameTest implements FabricGameTest {

    /** Room to build a floor and stand things on it inside the 8x8 empty structure. */
    private static final int FLOOR_Y = 1;

    /**
     * Physics, which is the assumption a whole debugging session was spent doubting. A smiler put
     * on a floor should be standing on it a few ticks later: {@code onGround} true and gravity
     * settled. It reads false for the first tick of any entity's life, and mistaking that for a
     * broken entity cost two rounds of diagnostics.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40)
    public void smilerSettlesOnTheFloor(TestContext context) {
        layFloor(context);
        SmilerEntity smiler = context.spawnEntity(ModEntities.SMILER_ENTITY, 3, FLOOR_Y + 1, 3);

        context.waitAndRun(10, () -> {
            context.assertTrue(smiler.isOnGround(),
                    "a smiler stood on a floor should be onGround after 10 ticks, not "
                            + smiler.isOnGround());
            context.assertTrue(smiler.getY() >= context.getAbsolutePos(new BlockPos(0, FLOOR_Y + 1, 0)).getY() - 0.5,
                    "a smiler should not have fallen through the floor, y=" + smiler.getY());
            context.complete();
        });
    }

    /** Floor below, air at the feet and air at the head. Anything else is not somewhere to stand. */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 20)
    public void isStandableWantsFloorAndHeadroom(TestContext context) {
        layFloor(context);

        Vec3d onFloor = feetOf(context, 3, 3);
        context.assertTrue(SmilerSpawner.isStandable(context.getWorld(), onFloor),
                "floor below with air above should be standable");

        // Feet inside a block.
        context.setBlockState(4, FLOOR_Y + 1, 3, Blocks.STONE);
        context.assertFalse(SmilerSpawner.isStandable(context.getWorld(), feetOf(context, 4, 3)),
                "a spot with its feet inside stone should not be standable");

        // Head inside a block, feet clear.
        context.setBlockState(5, FLOOR_Y + 2, 3, Blocks.STONE);
        context.assertFalse(SmilerSpawner.isStandable(context.getWorld(), feetOf(context, 5, 3)),
                "a spot with no headroom should not be standable");

        // Nothing underneath. The floor above is solid, so a hole has to be made.
        context.setBlockState(3, FLOOR_Y, 6, Blocks.AIR);
        context.assertFalse(SmilerSpawner.isStandable(context.getWorld(), feetOf(context, 3, 6)),
                "a spot with no floor under it should not be standable");

        context.complete();
    }

    /**
     * The predicate the whole design rests on. A smiler may only appear, and may only take its
     * unseen step, where no player can see — so a false negative here is a smiler materialising in
     * front of somebody.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40)
    public void isObservedFollowsWhereThePlayerLooks(TestContext context) {
        layFloor(context);

        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        BlockPos stand = context.getAbsolutePos(new BlockPos(1, FLOOR_Y + 1, 3));
        Vec3d ahead = feetOf(context, 6, 3);

        // Facing +X, which is yaw -90 in Minecraft's convention, so the spot at greater x is ahead.
        aim(player, stand, -90.0f);
        context.assertTrue(SmilerSpawner.isObserved(context.getWorld(), ahead),
                "a spot straight ahead of a player should be observed");

        // Same spot, player turned around.
        aim(player, stand, 90.0f);
        context.assertFalse(SmilerSpawner.isObserved(context.getWorld(), ahead),
                "a spot behind a player should not be observed");

        // Facing it again, but with a wall in the way.
        aim(player, stand, -90.0f);
        context.setBlockState(3, FLOOR_Y + 1, 3, Blocks.STONE);
        context.setBlockState(3, FLOOR_Y + 2, 3, Blocks.STONE);
        context.assertFalse(SmilerSpawner.isObserved(context.getWorld(), ahead),
                "a spot behind a wall should not be observed");

        context.complete();
    }

    /**
     * The spawner and the unseen step both ask about a <em>feet</em> position, because that is what
     * they are about to place a smiler at. If a ray ending on the floor's top face counts as
     * blocked, every such question answers "not observed" and a smiler is free to appear in plain
     * sight. This pins down which it is.
     */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40)
    public void isObservedWorksAtFeetLevelToo(TestContext context) {
        layFloor(context);

        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        BlockPos stand = context.getAbsolutePos(new BlockPos(1, FLOOR_Y + 1, 3));
        aim(player, stand, -90.0f);

        // Report the state rather than the conclusion: isObserved walks world.getPlayers(), so if
        // the mock player never landed in that list every answer is false for a reason that has
        // nothing to do with geometry.
        context.assertTrue(!context.getWorld().getPlayers().isEmpty(),
                "world.getPlayers() is empty, so isObserved can never see anything."
                        + " playerManager=" + context.getWorld().getServer().getPlayerManager()
                                .getPlayerList().size()
                        + " mockWorld=" + player.getWorld().getRegistryKey().getValue()
                        + " testWorld=" + context.getWorld().getRegistryKey().getValue());

        context.assertTrue(SmilerSpawner.isObserved(context.getWorld(), feetOf(context, 6, 3)),
                "a spot at feet level straight ahead should be observed."
                        + " players=" + context.getWorld().getPlayers().size()
                        + " alive=" + player.isAlive()
                        + " spectator=" + player.isSpectator()
                        + " yaw=" + player.getYaw()
                        + " headYaw=" + player.getHeadYaw()
                        + " eye=" + player.getEyePos()
                        + " look=" + player.getRotationVec(1.0f)
                        + " target=" + feetOf(context, 6, 3));
        context.complete();
    }

    /**
     * Put a player at a spot facing a given yaw. refreshPositionAndAngles alone was not enough -
     * the position took and the rotation did not - so yaw, head yaw and the previous-tick values
     * are all set explicitly, because getRotationVec reads whichever of them the entity last
     * settled on.
     */
    private static void aim(ServerPlayerEntity player, BlockPos at, float yaw) {
        player.refreshPositionAndAngles(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, yaw, 0.0f);
        player.setYaw(yaw);
        player.setHeadYaw(yaw);
        player.setBodyYaw(yaw);
        player.setPitch(0.0f);
        player.prevYaw = yaw;
        player.prevHeadYaw = yaw;
        player.prevPitch = 0.0f;
    }

    /** An 8x8 slab to stand on; the empty structure supplies nothing. */
    private static void layFloor(TestContext context) {
        for (int x = 0; x < 8; x++) {
            for (int z = 0; z < 8; z++) {
                context.setBlockState(x, FLOOR_Y, z, Blocks.STONE);
            }
        }
    }

    /** Centre of the block a thing standing at this relative column would occupy, in world space. */
    private static Vec3d feetOf(TestContext context, int x, int z) {
        BlockPos absolute = context.getAbsolutePos(new BlockPos(x, FLOOR_Y + 1, z));
        return new Vec3d(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
    }
}
