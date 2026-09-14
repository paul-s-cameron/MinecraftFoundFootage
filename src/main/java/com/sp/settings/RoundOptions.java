package com.sp.settings;

/**
 * Round rules the mod reads but does not own.
 *
 * <p>The lobby mod is what lets a host configure these, but the dependency only runs one way —
 * the lobby knows about this mod, never the reverse. So the seam is declared here: this mod
 * defines the questions and an answer for when it is running on its own, and whoever is present
 * {@linkplain #install installs} something better.
 *
 * <p>Values are read live rather than captured at the start of a round, so a change takes effect
 * without waiting for the next one. Callers should read through {@link #get()} at the point of
 * use rather than holding onto a {@link Source}.
 */
public final class RoundOptions {

    public interface Source {
        /** Whether a dead player riding along as a ghost can still be heard over voice chat. */
        boolean ghostsCanTalk();

        /**
         * Seconds to give every rally, overriding the level's own timing.
         *
         * <p>Zero means each level keeps the countdown its exit rule was registered with, which
         * is not uniform: the larger levels deliberately allow longer than the rest.
         */
        int rallyCountdownOverrideSeconds();
    }

    /** What this mod does with no lobby installed. Each answer is the pre-settings behaviour. */
    private static final Source DEFAULTS = new Source() {
        @Override
        public boolean ghostsCanTalk() {
            return true;
        }

        @Override
        public int rallyCountdownOverrideSeconds() {
            return 0;
        }
    };

    private static volatile Source source = DEFAULTS;

    private RoundOptions() {
    }

    /** Installs the authority on round rules. Passing null restores the standalone defaults. */
    public static void install(Source installed) {
        source = installed == null ? DEFAULTS : installed;
    }

    public static Source get() {
        return source;
    }
}
