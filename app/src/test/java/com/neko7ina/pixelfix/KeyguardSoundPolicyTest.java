package com.neko7ina.pixelfix;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class KeyguardSoundPolicyTest {
    /** No previous exit in this process. */
    private static final long NONE = Long.MAX_VALUE;

    @Test
    public void onlyTheSecondFinishOfOneUnlockIsTreatedAsDuplicate() {
        // Device evidence: the repeat arrives with the keyguard already hidden, 8 ms later.
        assertTrue(KeyguardSoundPolicy.isDuplicateExit(false, 8));
        assertTrue(KeyguardSoundPolicy.isDuplicateExit(false, 444));
        // A fresh unlock runs while the keyguard still shows, even right after another unlock.
        assertFalse(KeyguardSoundPolicy.isDuplicateExit(true, 8));
        assertFalse(KeyguardSoundPolicy.isDuplicateExit(true, 2563));
        // Hidden keyguard with no recent exit still finishes normally (observed 939 ms case).
        assertFalse(KeyguardSoundPolicy.isDuplicateExit(false, 939));
        assertFalse(KeyguardSoundPolicy.isDuplicateExit(false, NONE));
    }
}
