package com.neko7ina.pixelfix;

import android.media.SoundPool;
import android.os.SystemClock;
import android.util.Log;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

/**
 * Drops the repeated unlock sound of one unlock at the playback call itself. The keyguard plays
 * {@code mUnlockSoundId} through its own {@link SoundPool}; the surface-behind finish replays that
 * request once the keyguard is already hidden, which is the second sound. Hooking the keyguard exit
 * or its sound request proved unreliable because those call sites are inlined again, so this
 * watches the pool the mediator owns and matches the pool instance and the unlock sound id.
 */
final class KeyguardSoundFix {
    private static final String TAG = "PixelFix";
    private static final String MEDIATOR = "com.android.systemui.keyguard.KeyguardViewMediator";
    private static final long NEVER = Long.MIN_VALUE;

    private KeyguardSoundFix() {
    }

    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> mediator = loader.loadClass(MEDIATOR);
        Class<?> soundPool = loader.loadClass("android.media.SoundPool");
        Method play = soundPool.getDeclaredMethod("play", int.class, float.class, float.class,
                int.class, int.class, float.class);
        Method playSound = mediator.getDeclaredMethod("playSound", int.class);
        Field poolField = mediator.getDeclaredField("mLockSounds");
        poolField.setAccessible(true);
        Field unlockSoundId = mediator.getDeclaredField("mUnlockSoundId");
        unlockSoundId.setAccessible(true);
        Field keyguardShowing = mediator.getDeclaredField("mShowing");
        keyguardShowing.setAccessible(true);
        Constructor<?>[] constructors = mediator.getDeclaredConstructors();
        if (constructors.length != 1) {
            throw new IllegalStateException("Unexpected mediator constructors: "
                    + constructors.length);
        }
        Constructor<?> constructor = constructors[0];
        constructor.setAccessible(true);

        AtomicReference<Object> owner = new AtomicReference<>();
        AtomicReference<SoundPool> pool = new AtomicReference<>();
        AtomicLong unlockSound = new AtomicLong(-1);
        AtomicLong lastPlay = new AtomicLong(NEVER);
        AtomicBoolean skipped = new AtomicBoolean();
        AtomicBoolean observed = new AtomicBoolean();

        module.hook(constructor)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    owner.set(chain.getThisObject());
                    return result;
                });

        try {
            module.deoptimize(playSound);
        } catch (Throwable ignored) {
            // Best effort only; the pool hook does not depend on this caller.
        }

        module.hook(play)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object mediatorInstance = owner.get();
                    if (mediatorInstance == null) {
                        return chain.proceed();
                    }
                    if (pool.get() == null) {
                        // mLockSounds and mUnlockSoundId are published later, in setupLocked().
                        try {
                            SoundPool captured = (SoundPool) poolField.get(mediatorInstance);
                            int id = unlockSoundId.getInt(mediatorInstance);
                            if (captured != null && id != 0) {
                                pool.set(captured);
                                unlockSound.set(id);
                                module.log(Log.INFO, TAG,
                                        "Keyguard sound owner captured; unlockId=" + id);
                            }
                        } catch (Throwable error) {
                            module.log(Log.ERROR, TAG,
                                    "Keyguard sound owner unavailable; disabled", error);
                        }
                    }
                    if (pool.get() != chain.getThisObject()
                            || (int) chain.getArg(0) != unlockSound.get()) {
                        return chain.proceed();
                    }
                    try {
                        long now = SystemClock.uptimeMillis();
                        long previous = lastPlay.get();
                        long since = previous == NEVER ? Long.MAX_VALUE : now - previous;
                        boolean showing = keyguardShowing.getBoolean(mediatorInstance);
                        if (observed.compareAndSet(false, true)) {
                            module.log(Log.INFO, TAG,
                                    "Keyguard sound playback observed; keyguardShowing=" + showing);
                        }
                        if (KeyguardSoundPolicy.isDuplicateExit(showing, since)) {
                            if (skipped.compareAndSet(false, true)) {
                                module.log(Log.INFO, TAG,
                                        "Skipped duplicate unlock sound; sinceLast=" + since);
                            }
                            return 0;
                        }
                        lastPlay.set(now);
                    } catch (Throwable error) {
                        module.log(Log.ERROR, TAG,
                                "Keyguard sound check failed once; sound left unchanged", error);
                    }
                    return chain.proceed();
                });
        module.log(Log.INFO, TAG, "Keyguard sound hook installed");
    }
}
