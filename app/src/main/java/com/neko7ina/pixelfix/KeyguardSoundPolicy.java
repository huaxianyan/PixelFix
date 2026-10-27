package com.neko7ina.pixelfix;

/** Decides whether a keyguard sound request belongs to a duplicate unlock exit. */
final class KeyguardSoundPolicy {
    /** Observed duplicate intervals on this build: 56, 56, 412, 428, 442, 444 ms. */
    static final long DUPLICATE_WINDOW_MS = 800L;

    /**
     * Only the second request of the same unlock is dropped. A request without a previous
     * unlock sound always plays, so an unlock can never end up silent because of this rule.
     */
    static boolean shouldPlay(int requestedSoundId, int unlockSoundId,
            long sinceLastUnlockSoundMs) {
        if (requestedSoundId != unlockSoundId) {
            return true;
        }
        return sinceLastUnlockSoundMs >= DUPLICATE_WINDOW_MS;
    }
}
