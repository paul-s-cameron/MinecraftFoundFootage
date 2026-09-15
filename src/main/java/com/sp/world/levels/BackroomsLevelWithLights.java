package com.sp.world.levels;

import com.sp.world.levels.custom.Level0BackroomsLevel;

public interface BackroomsLevelWithLights {
    Level0BackroomsLevel.LightState getLightState();

    void setLightState(Level0BackroomsLevel.LightState lightState);

    /**
     * The state a level should come back as after a reload.
     *
     * <p>FLICKER and BLACKOUT are driven by an event, and {@code WorldEvents} persists the level's
     * state but <i>not</i> the event running it. Restoring one would leave the level stuck in it
     * with nothing left alive to turn the lights back on — a permanent blackout.
     *
     * <p>Also tolerates a missing or unrecognised value, which the raw {@code valueOf} did not:
     * a world saved before this field existed reads back as an empty string.
     */
    static LightState restored(String saved) {
        try {
            LightState state = LightState.valueOf(saved);
            return state == LightState.FLICKER || state == LightState.BLACKOUT ? LightState.ON : state;
        } catch (IllegalArgumentException malformedOrMissing) {
            return LightState.ON;
        }
    }

    enum LightState {
        ON,
        OFF,
        FLICKER,
        BLACKOUT
    }
}
