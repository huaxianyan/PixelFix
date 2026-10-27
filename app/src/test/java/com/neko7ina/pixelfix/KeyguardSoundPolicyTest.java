package com.neko7ina.pixelfix;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class KeyguardSoundPolicyTest {
    private static final int UNLOCK = 42;
    private static final int LOCK = 43;

    @Test
    public void repeatedUnlockSoundOfOneUnlockIsDroppedButFirstOneAlwaysPlays() {
        // No previous unlock sound yet: always play.
        assertTrue(KeyguardSoundPolicy.shouldPlay(UNLOCK, UNLOCK, Long.MAX_VALUE));
        assertTrue(KeyguardSoundPolicy.shouldPlay(UNLOCK, UNLOCK, 5000));
        // Duplicate inside the observed window (56..444 ms) is dropped.
        assertFalse(KeyguardSoundPolicy.shouldPlay(UNLOCK, UNLOCK, 56));
        assertFalse(KeyguardSoundPolicy.shouldPlay(UNLOCK, UNLOCK, 444));
        // After the window a new unlock may sound again.
        assertTrue(KeyguardSoundPolicy.shouldPlay(UNLOCK, UNLOCK, 900));
        // The lock sound is never touched.
        assertTrue(KeyguardSoundPolicy.shouldPlay(LOCK, UNLOCK, 10));
    }
}
