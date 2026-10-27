package com.neko7ina.pixelfix;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class KeyguardSoundPolicyTest {
    private static final int UNLOCK = 2;
    private static final int LOCK = 1;

    @Test
    public void onlyTheHiddenKeyguardRepeatOfOneUnlockIsDropped() {
        // Device evidence: the duplicate arrives with the keyguard already hidden, 8 ms later.
        assertFalse(KeyguardSoundPolicy.shouldPlay(UNLOCK, UNLOCK, false, 8));
        assertFalse(KeyguardSoundPolicy.shouldPlay(UNLOCK, UNLOCK, false, 444));
        // A fresh unlock runs while the keyguard still shows, even right after another unlock.
        assertTrue(KeyguardSoundPolicy.shouldPlay(UNLOCK, UNLOCK, true, 8));
        assertTrue(KeyguardSoundPolicy.shouldPlay(UNLOCK, UNLOCK, true, 2563));
        // Hidden keyguard with no recent unlock sound still plays (observed 939 ms case).
        assertTrue(KeyguardSoundPolicy.shouldPlay(UNLOCK, UNLOCK, false, 939));
        // The lock sound is never touched.
        assertTrue(KeyguardSoundPolicy.shouldPlay(LOCK, UNLOCK, false, 8));
    }
}
