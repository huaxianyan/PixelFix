package com.neko7ina.pixelfix;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
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
 * Keeps one unlock to one unlock sound.
 *
 * <p>The keyguard plays mUnlockSoundId through its own SoundPool, and the surface-behind finish
 * replays that request once the keyguard is already hidden, which is the second sound: such a
 * repeat is dropped. Some unlocks finish without that routine running at all and then no sound
 * plays; USER_PRESENT still arrives, so a missing sound is filled in once through the native
 * request path, which keeps the lockscreen sound setting in charge.
 */
final class KeyguardSoundFix {
    private static final String TAG = "PixelFix";
    private static final String MEDIATOR = "com.android.systemui.keyguard.KeyguardViewMediator";
    private static final long NEVER = Long.MIN_VALUE;
    /** Native play lands this long before USER_PRESENT; anything older means silence. */
    private static final long FILL_WINDOW_MS = 500L;

    private KeyguardSoundFix() {
    }

    static void install(XposedModule module, ClassLoader loader) throws ReflectiveOperationException {
        Class<?> mediator = loader.loadClass(MEDIATOR);
        Class<?> soundPool = loader.loadClass("android.media.SoundPool");
        Method play = soundPool.getDeclaredMethod("play", int.class, float.class, float.class,
                int.class, int.class, float.class);
        final Method playSound = mediator.getDeclaredMethod("playSound", int.class);
        Field poolField = mediator.getDeclaredField("mLockSounds");
        poolField.setAccessible(true);
        Field unlockSoundId = mediator.getDeclaredField("mUnlockSoundId");
        unlockSoundId.setAccessible(true);
        Field keyguardShowing = mediator.getDeclaredField("mShowing");
        keyguardShowing.setAccessible(true);
        Field contextField = mediator.getDeclaredField("mContext");
        contextField.setAccessible(true);
        playSound.setAccessible(true);
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
        AtomicBoolean receiverAttached = new AtomicBoolean();
        AtomicBoolean reported = new AtomicBoolean();

        module.hook(constructor)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    owner.set(chain.getThisObject());
                    return result;
                });

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
                                attachReceiver(module, contextField, mediatorInstance, playSound,
                                        unlockSound, lastPlay, receiverAttached, reported);
                            }
                        } catch (Throwable error) {
                            module.log(Log.ERROR, TAG, "Keyguard sound owner unavailable", error);
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
                        if (KeyguardSoundPolicy.isDuplicateExit(
                                keyguardShowing.getBoolean(mediatorInstance), since)) {
                            module.log(Log.INFO, TAG,
                                    "Skipped duplicate unlock sound; sinceLast=" + since);
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

    private static void attachReceiver(XposedModule module, Field contextField, Object mediator,
            Method playSound, AtomicLong unlockSound, AtomicLong lastPlay, AtomicBoolean attached,
            AtomicBoolean reported) throws ReflectiveOperationException {
        if (!attached.compareAndSet(false, true)) {
            return;
        }
        Context context = (Context) contextField.get(mediator);
        context.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context unused, Intent intent) {
                if (!Intent.ACTION_USER_PRESENT.equals(intent.getAction())) {
                    return;
                }
                long now = SystemClock.uptimeMillis();
                long previous = lastPlay.get();
                long since = previous == NEVER ? Long.MAX_VALUE : now - previous;
                if (since <= FILL_WINDOW_MS) {
                    return;
                }
                try {
                    // Fill through the native request so the sound setting still applies.
                    playSound.invoke(mediator, (int) unlockSound.get());
                    if (reported.compareAndSet(false, true)) {
                        module.log(Log.INFO, TAG,
                                "Filled missing unlock sound; sinceLast=" + since);
                    }
                } catch (Throwable error) {
                    module.log(Log.ERROR, TAG, "Could not fill the unlock sound once", error);
                }
            }
        }, new IntentFilter(Intent.ACTION_USER_PRESENT), Context.RECEIVER_EXPORTED);
        module.log(Log.INFO, TAG, "Unlock sound fill receiver attached");
    }
}
