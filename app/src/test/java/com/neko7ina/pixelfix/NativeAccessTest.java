package com.neko7ina.pixelfix;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class NativeAccessTest {
    @Test
    public void newDreamSelectsCurrentUsersPreviousPassiveFaceCycle() {
        // The first row is the captured second-dream failure: mode 8 survived
        // the previous face success. Other rows freeze the native/security boundary.
        Object[][] cases = {
                {"dream", "Dream started", true, 8, true, 0, 0, true},
                {"dream", "Dream started", true, 0, false, 0, 0, false},
                {"dream", "Dream started", true, 8, false, 0, 0, false},
                {"dream", "Dream started", false, 8, true, 0, 0, false},
                {"dream", "Dream started", true, 8, true, 10, 0, false},
                {"lockscreen", "Dream stopped", true, 8, true, 0, 0, false},
        };
        for (Object[] row : cases) {
            boolean selected = NativeAccess.isDreamStart(row[0], (String) row[1], "dream")
                    && NativeAccess.shouldResetFaceCycle((boolean) row[2], (int) row[3],
                            (boolean) row[4], (int) row[5], (int) row[6]);
            assertEquals(java.util.Arrays.toString(row), row[7], selected);
        }
    }

    @Test
    public void exitingDreamTargetsOnlyTheNativeLockscreenPath() {
        // Literal scene tokens are independent of production's reflected Scenes objects.
        Object[][] cases = {
                {"dream", "lockscreen", "Dream stopped", true},
                {"dream", "gone", "Dream stopped", false},
                {"dream", "communal", "Dream stopped", false},
                {"occluded", "lockscreen", "App occlusion stopped", false},
                {"lockscreen", "gone", "Device unlocked", false},
                {"dream", "lockscreen", "Power button", false},
        };
        for (Object[] row : cases) {
            assertEquals(row[0] + " -> " + row[1] + " (" + row[2] + ")", row[3],
                    NativeAccess.isDreamExit(row[0], row[1], (String) row[2],
                            "dream", "lockscreen"));
        }
    }
}
