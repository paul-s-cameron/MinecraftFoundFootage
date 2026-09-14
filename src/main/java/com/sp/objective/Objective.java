package com.sp.objective;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

/**
 * What the player is currently trying to do.
 *
 * <p>Server-authoritative and sent only when it changes. The distance and the countdown are
 * deliberately <i>not</i> in here as numbers — the client derives them every frame from
 * {@code target} and {@code deadlineTick} — so a whole rally costs one packet to open and one per
 * presence change, rather than one a second for as long as it runs.
 *
 * <p>Generic on purpose. The rally is only the first producer; collect, survive and find-X
 * objectives can be added later without the HUD learning anything new.
 */
public record Objective(String key, @Nullable Vec3d target, long deadlineTick, int present, int total) {

    /** Nothing to show: the overworld, the lobby, anywhere without an objective. */
    public static final Objective NONE = new Objective("", null, 0L, 0, 0);

    public boolean isEmpty() {
        return this.key.isEmpty();
    }

    public boolean hasTimer() {
        return this.deadlineTick > 0L;
    }

    public boolean hasPresence() {
        return this.total > 0;
    }
}
