package com.neko7ina.pixelfix;

import android.os.Build;
import android.os.Looper;
import android.util.Log;

import java.io.FileInputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;

public final class MainModule extends XposedModule {
    private static final String TAG = "PixelFix";
    private static final String SYSTEM_UI = "com.android.systemui";
    private boolean attempted;
    private volatile boolean dreamFailed;
    private volatile NativeAccess.Owner owner;
    private volatile NativeAccess.FaceCycleOwner faceCycleOwner;

    @Override
    public void onPackageReady(PackageReadyParam param) {
        if (!param.isFirstPackage() || !SYSTEM_UI.equals(param.getPackageName()) || attempted) {
            return;
        }
        attempted = true;
        if (Build.VERSION.SDK_INT != 37) {
            log(Log.WARN, TAG, "unsupported SDK; inactive; sdk=" + Build.VERSION.SDK_INT);
            return;
        }
        String digest;
        try {
            digest = sha256(param.getApplicationInfo().sourceDir);
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Cannot hash the SystemUI APK; inactive", error);
            return;
        }
        // Both fixes target this exact SystemUI APK. A fix needing another build brings its
        // own constants rather than widening this gate, and each fix still disables itself
        // alone when its native bindings fail.
        if (!BuildConfig.SYSTEMUI_BUILD.equals(Build.ID)
                || !BuildConfig.SYSTEMUI_SHA256.equals(digest)) {
            log(Log.WARN, TAG, "SystemUI build mismatch; inactive; build=" + Build.ID
                    + "; sha256=" + digest);
            return;
        }
        log(Log.INFO, TAG, "adaptation matched; module=" + BuildConfig.VERSION_NAME
                + "; build=" + Build.ID + "; sha256=" + digest);
        installDreamFix(param);
        installKeyguardSoundFix(param);
    }

    private void installDreamFix(PackageReadyParam param) {
        try {
            NativeAccess access = new NativeAccess(param.getClassLoader());
            // Both native callers validate before publishing their new scene. Deoptimizing
            // the callers makes the validation hook observable even with AOT compilation.
            if (!deoptimize(access.changeScene) || !deoptimize(access.snapToScene)) {
                throw new IllegalStateException("Cannot deoptimize native scene callers");
            }
            install(access);
            log(Log.INFO, TAG, "Dream lockscreen fix installed; waiting for native owners");
        } catch (Throwable error) {
            dreamFailed = true;
            log(Log.ERROR, TAG, "Dream lockscreen fix unavailable; inactive", error);
        }
    }

    private void installKeyguardSoundFix(PackageReadyParam param) {
        try {
            KeyguardSoundFix.install(this, param.getClassLoader());
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Keyguard sound fix unavailable; inactive", error);
        }
    }

    private void install(NativeAccess access) {
        XposedInterface.HookHandle capture = hook(access.timerConstructor)
                .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                .intercept(chain -> {
                    Object result = chain.proceed();
                    if (!dreamFailed) {
                        try {
                            owner = access.capture(chain.getThisObject());
                            log(Log.INFO, TAG, "timer owner captured");
                        } catch (Throwable error) {
                            fail("timer owner binding failed", error);
                        }
                    }
                    return result;
                });
        XposedInterface.HookHandle captureFace = null;
        try {
            captureFace = hook(access.biometricConstructor)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        if (!dreamFailed) {
                            try {
                                faceCycleOwner = access.captureFaceCycle(chain.getThisObject());
                                log(Log.INFO, TAG, "face-cycle owner captured");
                            } catch (Throwable error) {
                                fail("face-cycle owner binding failed", error);
                            }
                        }
                        return result;
                    });
            hook(access.validateSceneChange)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object accepted = chain.proceed();
                        if (!dreamFailed && Boolean.TRUE.equals(accepted)) {
                            boolean starting = NativeAccess.isDreamStart(chain.getArg(1),
                                    (String) chain.getArg(2), access.dream);
                            boolean exiting = NativeAccess.isDreamExit(chain.getArg(0), chain.getArg(1),
                                    (String) chain.getArg(2), access.dream, access.lockscreen);
                            if (starting || exiting) {
                                try {
                                    handleDreamBoundary(access, chain.getThisObject(), starting);
                                } catch (Throwable error) {
                                    fail("dream lifecycle repair failed; inspect native states", error);
                                }
                            }
                        }
                        return accepted;
                    });
        } catch (Throwable error) {
            if (captureFace != null) {
                captureFace.unhook();
            }
            capture.unhook();
            throw error;
        }
    }

    private void handleDreamBoundary(NativeAccess access, Object scene, boolean starting)
            throws ReflectiveOperationException {
        // The alarm receiver uses a null Handler (main looper). Never post this repair:
        // it must finish before the validated scene is published and before authentication.
        if (Looper.myLooper() != Looper.getMainLooper()) {
            fail("dream lifecycle arrived off main thread; repair disabled", null);
            return;
        }
        NativeAccess.Owner current = owner;
        if (current == null || current.scene() != scene) {
            fail("matching timer owner unavailable; repair disabled", null);
            return;
        }
        if (starting) {
            NativeAccess.FaceCycleOwner faceOwner = faceCycleOwner;
            if (faceOwner == null) {
                fail("face-cycle owner unavailable; repair disabled", null);
                return;
            }
            String result = access.resetFaceCycle(current, faceOwner, scene);
            log(Log.INFO, TAG, result == null
                    ? "dream start observed; no stale passive face cycle to finish" : result);
            return;
        }
        if (!access.hasLiveGrace(current)) {
            log(Log.INFO, TAG, "dream exit observed; no active awake grace to finish");
            return;
        }
        log(Log.INFO, TAG, "hit: native Dream stopped request accepted into lockscreen");
        log(Log.INFO, TAG, access.finishGrace(current));
    }

    private void fail(String message, Throwable error) {
        dreamFailed = true;
        if (error == null) {
            log(Log.ERROR, TAG, message);
        } else {
            log(Log.ERROR, TAG, message, error);
        }
    }

    private static String sha256(String path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (DigestInputStream input = new DigestInputStream(new FileInputStream(path), digest)) {
            byte[] buffer = new byte[64 * 1024];
            while (input.read(buffer) != -1) { /* Hash the installed APK without extracting it. */ }
        }
        StringBuilder hex = new StringBuilder(64);
        for (byte value : digest.digest()) {
            hex.append(Character.forDigit((value & 0xff) >>> 4, 16));
            hex.append(Character.forDigit(value & 0xf, 16));
        }
        return hex.toString();
    }
}
