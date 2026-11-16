package com.neko7ina.pixelfix;

/** 系统界面一启动就写一行自检摘要，门禁不通过时也照常输出。 */
final class Diagnostics {
    private static final String MEDIATOR = "com.android.systemui.keyguard.KeyguardViewMediator";
    private static final String DEVICE_ENTRY =
            "com.android.systemui.deviceentry.domain.interactor.DeviceEntryInteractor";

    private Diagnostics() {
    }

    /** 各修复依赖的符号是否还在；缺失时点名，方便系统升级后一眼看出来。 */
    static String targets(ClassLoader loader) {
        StringBuilder out = new StringBuilder();
        out.append("dream=").append(present(loader, MEDIATOR));
        out.append(" sound=").append(present(loader, MEDIATOR) && soundPlayPresent());
        out.append(" unlockScene=").append(present(loader, DEVICE_ENTRY));
        return out.toString();
    }

    private static boolean present(ClassLoader loader, String name) {
        try {
            Class.forName(name, false, loader);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }

    private static boolean soundPlayPresent() {
        try {
            android.media.SoundPool.class.getDeclaredMethod("play", int.class, float.class,
                    float.class, int.class, int.class, float.class);
            return true;
        } catch (Throwable error) {
            return false;
        }
    }
}
