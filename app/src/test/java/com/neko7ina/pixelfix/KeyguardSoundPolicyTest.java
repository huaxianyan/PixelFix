package com.neko7ina.pixelfix;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class KeyguardSoundPolicyTest {
    private static final int UNLOCK = 42;
    private static final int LOCK = 43;

    @Test
    public void duplicateExitAfterUnlockStaysSilentButNormalSoundsPlay() {
        // Second onKeyguardExitFinished of one unlock: keyguard already hidden.
        assertFalse(KeyguardSoundPolicy.shouldPlay(UNLOCK, UNLOCK, false));
        // First exit of the unlock, and every other sound, stay untouched.
        assertTrue(KeyguardSoundPolicy.shouldPlay(UNLOCK, UNLOCK, true));
        assertTrue(KeyguardSoundPolicy.shouldPlay(LOCK, UNLOCK, false));
    }
}
