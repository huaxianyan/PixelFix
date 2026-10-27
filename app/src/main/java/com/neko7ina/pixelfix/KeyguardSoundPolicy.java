package com.neko7ina.pixelfix;

/** Decides whether a keyguard sound request belongs to a duplicate unlock exit. */
final class KeyguardSoundPolicy {
    /** Observed duplicate intervals on this build: 8, 9, 56, 412, 428, 442, 444 ms. */
    static final long DUPLICATE_WINDOW_MS = 800L;

    /**
     * Only the repeated exit of one unlock is dropped, and it is recognised by both signals:
     * the keyguard is already hidden and the previous unlock sound is recent. A fresh unlock
     * runs while the keyguard is still showing, so a quick lock-and-unlock never loses its
     * sound even when it follows another unlock within the window.
     */
    static boolean shouldPlay(int requestedSoundId, int unlockSoundId, boolean keyguardShowing,
            long sinceLastUnlockSoundMs) {
        if (requestedSoundId != unlockSoundId) {
            return true;
        }
        return keyguardShowing || sinceLastUnlockSoundMs >= DUPLICATE_WINDOW_MS;
    }
}
