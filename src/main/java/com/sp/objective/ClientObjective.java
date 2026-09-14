package com.sp.objective;

/**
 * The client's copy of its own objective.
 *
 * <p>Cleared on disconnect. Without that, leaving a server while an objective was up would leave
 * the line burned onto the next server — one that may not have this mod at all, and so would never
 * send anything to correct it.
 */
public final class ClientObjective {

    private static Objective current = Objective.NONE;

    private ClientObjective() {
    }

    public static void set(Objective objective) {
        current = objective;
    }

    public static Objective get() {
        return current;
    }

    public static void clear() {
        current = Objective.NONE;
    }
}
