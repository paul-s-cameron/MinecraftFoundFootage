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

        /** Whether smilers come out at all during a blackout. */
        boolean smilersEnabled();

        /** How many smilers may be near one player at once. */
        int smilersPerPlayer();

        /** Whether a skinwalker may take a player nobody else can see. */
        boolean skinwalkerEnabled();

        /**
         * Seconds the lights stay out. One number for a random blackout and for the dark stretch
         * at the end of a rally, which are meant to be the same length.
         */
        int blackoutSeconds();

        /** Whether Level 1's lights fail on their own, as opposed to only when a rally asks. */
        boolean randomBlackouts();

        /** Whether gathering for a rally puts the lights out for its final stretch. */
        boolean rallyBlackout();
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

        @Override
        public boolean smilersEnabled() {
            return true;
        }

        @Override
        public int smilersPerPlayer() {
            return 3;
        }

        @Override
        public boolean skinwalkerEnabled() {
            return true;
        }

        @Override
        public int blackoutSeconds() {
            return 30;
        }

        @Override
        public boolean randomBlackouts() {
            return true;
        }

        @Override
        public boolean rallyBlackout() {
            return true;
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
