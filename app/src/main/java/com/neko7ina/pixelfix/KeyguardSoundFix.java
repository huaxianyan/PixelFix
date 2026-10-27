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
 * Drops the repeated finish of a single unlock. Native {@code onKeyguardExitFinished} hides the
 * keyguard, plays the unlock sound and sends USER_PRESENT; the surface-behind animation then
 * finishes the same exit again, which is the second sound. Only that repeat is skipped, decided by
 * the native state (keyguard already hidden) and a short window. Authentication, lock sounds and
 * every first exit stay untouched.
 */
final class KeyguardSoundFix {
    private static final String TAG = "PixelFix";
    private static final String MEDIATOR = "com.android.systemui.keyguard.KeyguardViewMediator";
    private static final long NEVER = Long.MIN_VALUE;

    private KeyguardSoundFix() {
    }

    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> mediator = loader.loadClass(MEDIATOR);
        Method exitFinished = mediator.getDeclaredMethod("onKeyguardExitFinished", String.class);
        exitFinished.setAccessible(true);
        Field keyguardShowing = mediator.getDeclaredField("mShowing");
        keyguardShowing.setAccessible(true);

        // Every caller is AOT compiled; without deoptimizing all of them the hook is bypassed by
        // inlining. Verified callers of onKeyguardExitFinished on this build:
        for (Method caller : new Method[] {
                mediator.getDeclaredMethod("handleHide"),
                mediator.getDeclaredMethod("handleStartKeyguardExitAnimationInner",
                        loader.loadClass("com.android.wm.shell.shared.compat.SurfaceTransition$Params")),
                loader.loadClass(MEDIATOR + "$$ExternalSyntheticLambda3").getDeclaredMethod("run"),
                loader.loadClass(MEDIATOR + "$$ExternalSyntheticLambda15").getDeclaredMethod("invoke"),
        }) {
            caller.setAccessible(true);
            if (!module.deoptimize(caller)) {
                throw new IllegalStateException("Cannot deoptimize " + caller.getName());
            }
        }

        Map<Object, Long> lastExit = new WeakHashMap<>();
        AtomicBoolean skipped = new AtomicBoolean();
        AtomicBoolean failed = new AtomicBoolean();
        module.hook(exitFinished)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    if (failed.get()) {
                        return chain.proceed();
                    }
                    try {
                        Object instance = chain.getThisObject();
                        long now = SystemClock.uptimeMillis();
                        long previous;
                        synchronized (lastExit) {
                            Long stored = lastExit.get(instance);
                            previous = stored == null ? NEVER : stored;
                        }
                        long since = previous == NEVER ? Long.MAX_VALUE : now - previous;
                        if (KeyguardSoundPolicy.isDuplicateExit(
                                keyguardShowing.getBoolean(instance), since)) {
                            if (skipped.compareAndSet(false, true)) {
                                module.log(Log.INFO, TAG,
                                        "Skipped repeated keyguard exit; sinceLast=" + since);
                            }
                            return null;
                        }
                        synchronized (lastExit) {
                            lastExit.put(instance, now);
                        }
                    } catch (Throwable error) {
                        failed.set(true);
                        module.log(Log.ERROR, TAG,
                                "Keyguard exit adjustment disabled; leaving it unchanged", error);
                    }
                    return chain.proceed();
                });
        module.log(Log.INFO, TAG, "Keyguard exit hook installed");
    }
}
