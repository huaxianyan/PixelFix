package com.neko7ina.pixelfix;

/** Decides whether a keyguard exit is the repeated finish of one unlock. */
final class KeyguardSoundPolicy {
    /** Observed duplicate spacing on this build: 8, 9, 56, 412, 428, 442, 444 ms. */
    static final long DUPLICATE_WINDOW_MS = 800L;

    /**
     * Native code finishes the keyguard again when the surface-behind animation completes, and the
     * keyguard is already hidden by then; that repeat plays the unlock sound a second time and
     * sends a second USER_PRESENT. A fresh unlock always runs while the keyguard still shows, so a
     * quick lock-and-unlock keeps its sound even right after another unlock.
     */
    static boolean isDuplicateExit(boolean keyguardShowing, long sinceLastExitMs) {
        return !keyguardShowing && sinceLastExitMs < DUPLICATE_WINDOW_MS;
    }
}
