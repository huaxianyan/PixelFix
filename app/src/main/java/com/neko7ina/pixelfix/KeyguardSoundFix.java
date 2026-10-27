package com.neko7ina.pixelfix;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Stops the second unlock sound of a single unlock. Native {@code onKeyguardExitFinished} plays
 * {@code mUnlockSoundId} before hiding the keyguard, then the surface-behind finish runs it again
 * when {@code mShowing} is already false. Only that duplicate is skipped; the lock sound, the
 * USER_PRESENT broadcasts and every unlock decision stay untouched.
 */
final class KeyguardSoundFix {
    private static final String TAG = "PixelFix";

    private KeyguardSoundFix() {
    }

    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> mediator = loader.loadClass("com.android.systemui.keyguard.KeyguardViewMediator");
        Method playSound = mediator.getDeclaredMethod("playSound", int.class);
        playSound.setAccessible(true);
        Field keyguardShowing = mediator.getDeclaredField("mShowing");
        keyguardShowing.setAccessible(true);
        Field unlockSoundId = mediator.getDeclaredField("mUnlockSoundId");
        unlockSoundId.setAccessible(true);

        AtomicBoolean skipped = new AtomicBoolean();
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
                        if (!KeyguardSoundPolicy.shouldPlay(requested,
                                unlockSoundId.getInt(instance),
                                keyguardShowing.getBoolean(instance))) {
                            if (skipped.compareAndSet(false, true)) {
                                module.log(Log.INFO, TAG,
                                        "Skipped duplicate unlock sound; keyguard already hidden");
                            }
                            return null;
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
