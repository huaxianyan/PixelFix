package com.neko7ina.pixelfix;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

/** Exact bindings for the SystemUI APK accepted by MainModule. No alternate signatures. */
final class NativeAccess {
    private static final String INTERACTOR = "com.android.systemui.keyguard.domain.interactor.";
    final Constructor<?> timerConstructor;
    final Constructor<?> biometricConstructor;
    final Method validateSceneChange;
    final Method changeScene;
    final Method snapToScene;
    final Object dream;
    final Object lockscreen;
    final Object running;
    final Object inactive;
    private final Field repository;
    private final Field timerScene;
    private final Field timerState;
    private final Field permission;
    private final Field counter;
    private final Field power;
    private final Field wakefulness;
    private final Method flowValue;
    private final Method flowSetValue;
    private final Method readonlyValue;
    private final Method isAwake;
    private final Method stopTimer;
    private final Field biometricInteractor;
    private final Field biometricRepository;
    private final Field biometricScene;
    private final Field selectedUser;
    private final Field biometricState;
    private final Field controllerMode;
    private final Field modelMode;
    private final Field modelSource;
    private final Field modelUser;
    private final Method lazyGet;
    private final Method selectedUserId;
    private final Method resetMode;
    private final Object noneUnlocked;
    private final Object none;
    private final Object face;

    NativeAccess(ClassLoader loader) throws ReflectiveOperationException {
        Class<?> timer = loader.loadClass(INTERACTOR + "LockAfterDelayInteractor");
        Class<?> repo = loader.loadClass("com.android.systemui.keyguard.data.repository.KeyguardRepositoryImpl");
        Class<?> scene = loader.loadClass("com.android.systemui.scene.domain.interactor.LegacySceneInteractorImpl");
        Class<?> sceneKey = loader.loadClass("com.android.compose.animation.scene.SceneKey");
        Class<?> transitionKey = loader.loadClass("com.android.compose.animation.scene.TransitionKey");
        Class<?> keyguardState = loader.loadClass("com.android.systemui.keyguard.shared.model.KeyguardState");
        Class<?> hideOverlay = loader.loadClass("com.android.systemui.scene.domain.interactor.SceneInteractor$HideOverlayCommand");
        Class<?> stateFlow = loader.loadClass("kotlinx.coroutines.flow.StateFlowImpl");
        Class<?> readonlyFlow = loader.loadClass("kotlinx.coroutines.flow.ReadonlyStateFlow");
        Class<?> powerClass = loader.loadClass("com.android.systemui.power.domain.interactor.PowerInteractor");
        Class<?> wakeModel = loader.loadClass("com.android.systemui.power.shared.model.WakefulnessModel");
        Class<?> scenes = loader.loadClass("com.android.systemui.scene.shared.model.Scenes");
        Class<?> timerStates = loader.loadClass("com.android.systemui.keyguard.shared.model.LockAfterDelayTimerState");
        Class<?> biometric = loader.loadClass("com.android.systemui.statusbar.phone.BiometricUnlockController");
        Class<?> biometricInteractorClass = loader.loadClass(INTERACTOR + "BiometricUnlockInteractor");
        Class<?> model = loader.loadClass("com.android.systemui.keyguard.shared.model.BiometricUnlockModel");
        Class<?> mode = loader.loadClass("com.android.systemui.keyguard.shared.model.BiometricUnlockMode");
        Class<?> source = loader.loadClass("com.android.systemui.keyguard.shared.model.BiometricUnlockSource");
        Class<?> lazy = loader.loadClass("dagger.Lazy");
        Class<?> clock = loader.loadClass("com.android.systemui.util.time.SystemClockImpl");

        timerConstructor = timer.getDeclaredConstructor(
                loader.loadClass("kotlinx.coroutines.CoroutineScope"),
                android.content.Context.class,
                loader.loadClass("com.android.systemui.util.PendingIntentCreatorImpl"), repo,
                loader.loadClass("com.android.systemui.authentication.domain.interactor.AuthenticationInteractor"),
                clock,
                android.app.AlarmManager.class,
                loader.loadClass("com.android.systemui.util.settings.repository.UserAwareSecureSettingsRepository"),
                loader.loadClass("com.android.systemui.util.settings.repository.UserAwareSystemSettingsRepository"),
                powerClass,
                loader.loadClass("com.android.systemui.scene.domain.interactor.SceneInteractor"),
                loader.loadClass("com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor"),
                loader.loadClass(INTERACTOR + "TrustInteractor"));
        biometricConstructor = biometric.getDeclaredConstructor(
                loader.loadClass("com.android.systemui.statusbar.phone.DozeScrimController"),
                loader.loadClass("com.android.systemui.keyguard.KeyguardViewMediator"),
                loader.loadClass("com.android.systemui.statusbar.NotificationShadeWindowController"),
                loader.loadClass("com.android.systemui.statusbar.policy.KeyguardStateController"),
                android.os.Handler.class,
                loader.loadClass("com.android.keyguard.KeyguardUpdateMonitor"),
                android.content.res.Resources.class,
                loader.loadClass("com.android.systemui.statusbar.phone.KeyguardBypassController"),
                loader.loadClass("com.android.internal.logging.MetricsLogger"),
                loader.loadClass("com.android.systemui.dump.DumpManager"),
                android.os.PowerManager.class,
                loader.loadClass("com.android.keyguard.logging.BiometricUnlockLogger"),
                loader.loadClass("com.android.systemui.keyguard.WakefulnessLifecycle"),
                loader.loadClass("com.android.systemui.biometrics.AuthController"),
                loader.loadClass("com.android.systemui.plugins.statusbar.StatusBarStateController"),
                loader.loadClass("com.android.systemui.log.SessionTracker"),
                loader.loadClass("com.android.internal.util.LatencyTracker"),
                loader.loadClass("com.android.systemui.statusbar.phone.ScreenOffAnimationController"),
                loader.loadClass("com.android.systemui.statusbar.VibratorHelper"), clock, lazy,
                biometricInteractorClass,
                loader.loadClass("com.android.systemui.util.kotlin.JavaAdapter"),
                loader.loadClass(INTERACTOR + "KeyguardTransitionInteractor"), lazy, lazy, lazy);
        validateSceneChange = method(scene, "validateSceneChange", boolean.class,
                sceneKey, sceneKey, String.class);
        changeScene = method(scene, "changeScene", void.class,
                sceneKey, String.class, transitionKey, keyguardState, boolean.class, hideOverlay);
        snapToScene = method(scene, "snapToScene", void.class,
                sceneKey, String.class, hideOverlay, keyguardState, boolean.class);
        stopTimer = method(timer, "ensureTimerStopped", void.class, String.class);
        repository = field(timer, "repository", repo);
        timerScene = field(timer, "sceneInteractor",
                loader.loadClass("com.android.systemui.scene.domain.interactor.SceneInteractor"));
        timerState = field(repo, "lockAfterDelayState", stateFlow);
        permission = field(repo, "_canIgnoreAuthAndReturnToGone", stateFlow);
        counter = field(timer, "timeoutCounter", AtomicInteger.class);
        power = field(timer, "powerInteractor", powerClass);
        wakefulness = field(powerClass, "detailedWakefulness", readonlyFlow);
        flowValue = method(stateFlow, "getValue", Object.class);
        flowSetValue = method(stateFlow, "setValue", void.class, Object.class);
        readonlyValue = method(readonlyFlow, "getValue", Object.class);
        isAwake = method(wakeModel, "isAwake", boolean.class);
        dream = field(scenes, "Dream", sceneKey).get(null);
        lockscreen = field(scenes, "Lockscreen", sceneKey).get(null);
        running = field(timerStates, "RUNNING", timerStates).get(null);
        inactive = field(timerStates, "INACTIVE", timerStates).get(null);
        biometricInteractor = field(biometric, "mBiometricUnlockInteractor", biometricInteractorClass);
        biometricRepository = field(biometricInteractorClass, "keyguardRepository", repo);
        biometricScene = field(biometric, "mSceneInteractor", lazy);
        selectedUser = field(biometric, "mSelectedUserInteractor", lazy);
        biometricState = field(repo, "_biometricUnlockState", stateFlow);
        controllerMode = field(biometric, "mMode", int.class);
        modelMode = field(model, "mode", mode);
        modelSource = field(model, "source", source);
        modelUser = field(model, "userId", int.class);
        lazyGet = method(lazy, "get", Object.class);
        selectedUserId = method(loader.loadClass(
                "com.android.systemui.user.domain.interactor.SelectedUserInteractorImpl"),
                "getSelectedUserId", int.class);
        resetMode = method(biometric, "resetMode", void.class);
        noneUnlocked = field(mode, "NONE_UNLOCKED", mode).get(null);
        none = field(mode, "NONE", mode).get(null);
        face = field(source, "FACE_SENSOR", source).get(null);
    }

    Owner capture(Object timer) throws ReflectiveOperationException {
        Object repo = repository.get(timer);
        return new Owner(timer, repo, timerScene.get(timer), timerState.get(repo), permission.get(repo),
                (AtomicInteger) counter.get(timer), wakefulness.get(power.get(timer)));
    }

    static boolean isDreamExit(Object from, Object to, String reason,
            Object dream, Object lockscreen) {
        return dream.equals(from) && lockscreen.equals(to) && "Dream stopped".equals(reason);
    }

    FaceCycleOwner captureFaceCycle(Object controller) throws ReflectiveOperationException {
        return new FaceCycleOwner(controller,
                biometricRepository.get(biometricInteractor.get(controller)),
                biometricScene.get(controller), selectedUser.get(controller));
    }

    static boolean isDreamStart(Object to, String reason, Object dream) {
        return dream.equals(to) && "Dream started".equals(reason);
    }

    static boolean shouldResetFaceCycle(boolean awake, int controllerMode,
            boolean matchingPassiveFaceModel, int modelUser, int selectedUser) {
        // Native MODE_NONE_UNLOCKED is 8 in the pinned APK. This is bookkeeping,
        // not an authentication result: a later genuine success must produce the next mode.
        return awake && controllerMode == 8 && matchingPassiveFaceModel && modelUser == selectedUser;
    }

    String resetFaceCycle(Owner owner, FaceCycleOwner biometric, Object scene)
            throws ReflectiveOperationException {
        if (biometric.repository != owner.repository || lazyGet.invoke(biometric.scene) != scene) {
            throw new IllegalStateException("Biometric owner belongs to a different object graph");
        }
        Object state = biometricState.get(owner.repository);
        Object oldModel = flowValue.invoke(state);
        int user = (int) selectedUserId.invoke(lazyGet.invoke(biometric.selectedUser));
        if (!shouldResetFaceCycle(
                Boolean.TRUE.equals(isAwake.invoke(readonlyValue.invoke(owner.wakefulness))),
                controllerMode.getInt(biometric.controller),
                noneUnlocked.equals(modelMode.get(oldModel)) && face.equals(modelSource.get(oldModel)),
                modelUser.getInt(oldModel), user)) {
            return null;
        }
        // A new dream ends the previous passive-face cycle. Use its native owner so
        // the controller and repository stay in sync. NONE maps to no unlock source.
        resetMode.invoke(biometric.controller);
        Object newModel = flowValue.invoke(state);
        if (controllerMode.getInt(biometric.controller) != 0
                || !none.equals(modelMode.get(newModel)) || modelSource.get(newModel) != null
                || modelUser.getInt(newModel) != user) {
            throw new IllegalStateException("Native mode reset did not finish consistently");
        }
        return "face cycle ended on dream start; " + oldModel + " -> " + newModel;
    }

    boolean hasLiveGrace(Owner owner) throws ReflectiveOperationException {
        return running.equals(flowValue.invoke(owner.state))
                && Boolean.TRUE.equals(flowValue.invoke(owner.permission))
                && Boolean.TRUE.equals(isAwake.invoke(readonlyValue.invoke(owner.wakefulness)));
    }

    String finishGrace(Owner owner) throws ReflectiveOperationException {
        int oldSequence = owner.counter.get();
        // Native owner invalidates the old alarm and publishes INACTIVE. INACTIVE alone
        // does not clear the permission: the native collector handles only RUNNING/ELAPSED.
        stopTimer.invoke(owner.timer, "dream stopped into validated lockscreen (DreamLockscreenFix)");
        flowSetValue.invoke(owner.permission, Boolean.FALSE);
        Object state = flowValue.invoke(owner.state);
        Object permit = flowValue.invoke(owner.permission);
        int sequence = owner.counter.get();
        if (!inactive.equals(state) || !Boolean.FALSE.equals(permit) || sequence != oldSequence + 1) {
            throw new IllegalStateException("Unexpected repair result: state=" + state
                    + ", permission=" + permit + ", seq=" + oldSequence + "->" + sequence);
        }
        return "grace finished; state=" + state + ", permission=" + permit
                + ", alarmSeq=" + oldSequence + "->" + sequence;
    }

    private static Field field(Class<?> owner, String name, Class<?> type)
            throws ReflectiveOperationException {
        Field field = owner.getDeclaredField(name);
        if (field.getType() != type) {
            throw new NoSuchFieldException(owner.getName() + "." + name + " type mismatch");
        }
        field.setAccessible(true);
        return field;
    }

    private static Method method(Class<?> owner, String name, Class<?> result, Class<?>... args)
            throws ReflectiveOperationException {
        Method method = owner.getDeclaredMethod(name, args);
        if (method.getReturnType() != result) {
            throw new NoSuchMethodException(owner.getName() + "." + name + " return type mismatch");
        }
        method.setAccessible(true);
        return method;
    }

    record Owner(Object timer, Object repository, Object scene, Object state, Object permission,
                 AtomicInteger counter, Object wakefulness) { }

    record FaceCycleOwner(Object controller, Object repository, Object scene, Object selectedUser) { }
}
