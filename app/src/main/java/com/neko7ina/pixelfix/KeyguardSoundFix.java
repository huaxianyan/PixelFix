package com.neko7ina.pixelfix;

import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Stops the repeated unlock sound of a single unlock. Native {@code onKeyguardExitFinished} plays
 * {@code mUnlockSoundId}, and the surface-behind finish runs that routine again, which is the
 * second sound. Only a second unlock sound inside {@link KeyguardSoundPolicy#DUPLICATE_WINDOW_MS}
 * is dropped; a first sound always plays, and broadcasts, authentication and lock sounds stay
 * untouched.
 */
final class KeyguardSoundFix {
    private static final String TAG = "PixelFix";
    private static final long NEVER = Long.MIN_VALUE;

    private KeyguardSoundFix() {
    }

    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> mediator = loader.loadClass("com.android.systemui.keyguard.KeyguardViewMediator");
        Method playSound = mediator.getDeclaredMethod("playSound", int.class);
        playSound.setAccessible(true);
        Field unlockSoundId = mediator.getDeclaredField("mUnlockSoundId");
        unlockSoundId.setAccessible(true);
        Method exitFinished = mediator.getDeclaredMethod("onKeyguardExitFinished", String.class);
        exitFinished.setAccessible(true);
        // The caller is AOT compiled; without this the hook on playSound is bypassed by inlining.
        if (!module.deoptimize(exitFinished)) {
            throw new IllegalStateException("Cannot deoptimize the keyguard exit caller");
        }

        Map<Object, Long> lastUnlockSound = new WeakHashMap<>();
        AtomicBoolean skipped = new AtomicBoolean();
        AtomicBoolean observed = new AtomicBoolean();
        AtomicBoolean failed = new AtomicBoolean();
        module.hook(playSound)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    if (failed.get()) {
                        return chain.proceed();
                    }
                    try {
                        Object instance = chain.getThisObject();
                        int requested = (int) chain.getArg(0);
                        int unlockId = unlockSoundId.getInt(instance);
                        if (requested == unlockId) {
                            long now = SystemClock.uptimeMillis();
                            long previous;
                            synchronized (lastUnlockSound) {
                                Long stored = lastUnlockSound.get(instance);
                                previous = stored == null ? NEVER : stored;
                            }
                            long since = previous == NEVER ? Long.MAX_VALUE : now - previous;
                            if (observed.compareAndSet(false, true)) {
                                module.log(Log.INFO, TAG, "Keyguard sound interceptor active; "
                                        + "unlockId=" + unlockId + "; sinceLast="
                                        + (previous == NEVER ? "none" : since));
                            }
                            if (!KeyguardSoundPolicy.shouldPlay(requested, unlockId, since)) {
                                if (skipped.compareAndSet(false, true)) {
                                    module.log(Log.INFO, TAG,
                                            "Skipped duplicate unlock sound; sinceLast=" + since);
                                }
                                return null;
                            }
                            synchronized (lastUnlockSound) {
                                lastUnlockSound.put(instance, now);
                            }
                        }
                    } catch (Throwable error) {
                        failed.set(true);
                        module.log(Log.ERROR, TAG,
                                "Keyguard sound adjustment disabled; leaving sounds unchanged",
                                error);
                    }
                    return chain.proceed();
                });
        module.log(Log.INFO, TAG, "Keyguard sound hook installed");
    }
}
