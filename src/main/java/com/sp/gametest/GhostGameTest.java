package com.sp.gametest;

import com.sp.cca_stuff.InitializeComponents;
import com.sp.cca_stuff.PlayerComponent;
import com.sp.ghost.GhostManager;
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.test.GameTest;
import net.minecraft.test.TestContext;

/**
 * The set of ghost ids that the voice chat plugin routes by.
 *
 * <p>Worth pinning down because what it guards against is invisible in play, and only in the
 * dangerous direction: a ghost missing from the set is not silent, they are audible to everyone
 * still in the run — the whole bug the routing exists to prevent. And it is the state most likely
 * to drift, because ghost state is persisted, so a server restart brings back ghosts whose deaths
 * this process never saw. The tick has to adopt them rather than trust that it watched every
 * transition.
 */
public class GhostGameTest implements FabricGameTest {

    /** A player whose component says ghost, without this process ever having seen them die. */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40)
    public void tickAdoptsAGhostItNeverSawDie(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        PlayerComponent component = InitializeComponents.PLAYER.get(player);

        context.assertFalse(GhostManager.isGhost(player.getUuid()),
                "a player who has not died should not be in the ghost set");

        // Set on the component alone, which is what loading one from disk looks like.
        component.setGhost(true);
        GhostManager.tick(player, component);
        context.assertTrue(GhostManager.isGhost(player.getUuid()),
                "the tick should have adopted a ghost it never saw die - without this, a ghost"
                        + " who survived a restart is heard by everyone still in the run");

        cleanUp(context, player, component);
        context.complete();
    }

    /** And the other way, so a revived player is not left in the dead's channel. */
    @GameTest(templateName = FabricGameTest.EMPTY_STRUCTURE, tickLimit = 40)
    public void tickDropsAPlayerWhoIsNoLongerAGhost(TestContext context) {
        ServerPlayerEntity player = context.createMockCreativeServerPlayerInWorld();
        PlayerComponent component = InitializeComponents.PLAYER.get(player);

        component.setGhost(true);
        GhostManager.tick(player, component);
        context.assertTrue(GhostManager.isGhost(player.getUuid()), "should be a ghost first");

        component.setGhost(false);
        GhostManager.tick(player, component);
        context.assertFalse(GhostManager.isGhost(player.getUuid()),
                "a player who is no longer a ghost should be out of the set");

        cleanUp(context, player, component);
        context.complete();
    }

    /**
     * A mock player outlives the test that made them, and so would their ghost state — the smiler
     * tests would then see an extra pair of eyes in the world, which is exactly how adding this
     * class first broke them. Both are put back.
     */
    private static void cleanUp(TestContext context, ServerPlayerEntity player,
                                PlayerComponent component) {
        component.setGhost(false);
        GhostManager.tick(player, component);
        context.getWorld().getServer().getPlayerManager().remove(player);
    }
}
