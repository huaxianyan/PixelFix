package com.neko7ina.pixelfix;

/** Decides whether a keyguard sound request may play, from native state only. */
final class KeyguardSoundPolicy {
    /**
     * The unlock sound belongs to the first exit of an unlock. Native code calls
     * {@code onKeyguardExitFinished} again when the surface-behind animation finishes, and the
     * keyguard is already hidden by then, which is the duplicate that plays a second time.
     */
    static boolean shouldPlay(int requestedSoundId, int unlockSoundId, boolean keyguardShowing) {
        return !(requestedSoundId == unlockSoundId && !keyguardShowing);
    }
}
