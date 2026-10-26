# 构建与待测安装包

> 本文件自本地 Android 问题工作区迁移，记录的是迁移前「屏保锁屏修复」独立模块时期的调查与验收。文中的本地目录（`.work/...`）与旧包名属于迁移前状态，现包名为 `com.neko7ina.pixelfix`，版本见 [开发进度](../../STATUS.md)。

## 本轮产物

- 版本：`0.2.0`，`versionCode=2`。`0.1.0` 的首轮完整验收未通过，本包增加新一轮屏保开始时的旧人脸模式重置。
- 包名（迁移前）：`com.neko7ina.dreamlockscreenfix`，名称「屏保锁屏修复」。现为 `com.neko7ina.pixelfix`，名称「Pixel Fix」。没有桌面入口或配置页，在 LSPosed 管理器中管理。
- 本地文件：`.work/dream-lockscreen/DreamLockscreenFix-0.2.0.apk`，路径相对工作区根目录。
- 大小：34813 字节。
- APK SHA-256：`2b8531273b5986ab5245ac9d2ceaca1f81889ee806c64ac1ea1d2a17043b4db6`。
- 签名：本机 Android 调试签名，证书 SHA-256 为 `9590d5111a9e3bb8ec331215365723bc5dc01d378fcfbb8b40a2d25098100e71`。这是本地测试包，不是正式发布包。后续覆盖安装保持此签名身份。
- 适用范围：Android 17 `CP3A.261005.005`，并要求运行中的 SystemUI APK 与取证包 SHA-256 相同。白名单的唯一定义在 `app/build.gradle.kts`，生成到 BuildConfig。
- 框架要求：支持 libxposed API 102。静态作用域只有 `com.android.systemui`。

APK、本地测试报告和构建日志都保留在 `.work/dream-lockscreen/`。不入 Git，不发布到远程。

## 构建

使用共用 JDK 17 与 Android SDK，路径见本地机器档案。工程固定 Gradle 9.7.0、AGP 9.1.0、libxposed API 102.0.0。平台为 Android 37.2，目标 SDK 和最低 SDK 都是 37。

从工作区根目录执行，先把 `JAVA_HOME` 和 `ANDROID_HOME` 指向既有共用环境。若进程继承了失效的 `ANDROID_SDK_ROOT`，先取消该值。

```bash
cd issues/dream-lockscreen
./gradlew --offline --console=plain \
  --project-cache-dir ../../.work/dream-lockscreen/gradle-cache \
  :app:testDebugUnitTest :app:assembleDebug
```

首次无缓存时去掉 `--offline`。依赖仓库包含本机已有缓存对应的 Maven 镜像。Java 工程显式关闭 AGP 内置 Kotlin，不引入无用途的 Kotlin 标准库。

输出与报告相对工作区根目录：

- APK：`.work/dream-lockscreen/build/app/outputs/apk/debug/app-debug.apk`。
- 单元测试：`.work/dream-lockscreen/build/app/test-results/testDebugUnitTest/`。
- 本轮构建日志：`.work/dream-lockscreen/build-0.2.0-log.txt`。

本轮构建通过，耗时 25 秒。2 条单元测试通过，各覆盖 6 组条件，仅证明选择条件。新增反射绑定已逐项对照取证 DEX，核对中修正了人脸来源枚举名 FACE_SENSOR。成品签名、包名、SDK 和版本检查通过，签名身份与旧包一致。作用域配置保持原样。

构建检查本身不验证运行时行为。随后经当次授权安装、由用户重启，设备日志确认两个所有者捕获、旧模式重置和再次真实认证恢复解锁。用户数次复测未再触发，本轮复测通过。准确次数和长期效果未知，完整回归表尚未逐项验收。详见 [验收记录](VALIDATION.md)。

构建仍有工具链警告：AGP 9.1.0 的已测试上限为 SDK 36.1，D8 提示尚不支持 API 37。APK 已生成并通过签名检查，模块代码没有调用新增的 API 37 接口，但这些结果不能替代 Android 17 上的加载验收。没有压低目标 SDK 或屏蔽警告。

## 0.2.0 验收步骤

本地开发阶段只修改与构建，未操作设备。随后按用户当次授权覆盖安装，用户自行重启，助手只读检查加载和复测日志。旧版 PIN 界面与新版正常传播的日志均已保存，后续设备变更仍须当次授权。

下列清单保留为验收入口。加载、重置和重复人脸认证路径本轮已有证据，不重复执行已通过的检查；其他回归项与长期使用分开记录。

1. 确认系统仍为适配构建，框架支持 API 102。记录当前模块清单，仅给本修复选择 SystemUI 作用域。
2. 用户确认后安装上述测试 APK，启用模块并按框架要求重启。保持其他模块和人脸设置原样。
3. 检查 `DreamLockscreenFix` 日志中的 `module=0.2.0`、`hooks installed`、`timer owner captured` 和 `face-cycle owner captured`。两个所有者捕获日志的先后不限。
4. 按 [验收计划](VALIDATION.md) 测试。首次退出如有活动宽限，仍应出现 `hit:` 与 `grace finished`，含 `state=INACTIVE`、`permission=false` 和旧、新闹钟序号。
5. 人脸通过后先停在锁屏，再进入一轮屏保。开始时应出现 `face cycle ended on dream start`，记录旧的人脸成功模式变为 NONE、来源清空。退出后完成真实人脸认证，上滑应能进入桌面，锁头和设备进入状态需一致。
6. 跨过旧宽限截止时刻，并检查正常人脸、指纹和 PIN 入口。次数和结果分别记录。旧包虽已加载并命中过，不能用作新包的验收证据。

`dream exit observed; no active awake grace to finish` 表示该次未满足活动宽限条件，不代表模式重置成功。`dream start observed; no stale passive face cycle to finish` 表示没有选中旧人脸模式。返回桌面或 Communal 的原生退出路径不清理宽限。不能仅凭没有复现故障判定补丁有效。

遇到适配拒绝、线程不符、所有者缺失或修复错误，先保存日志。模块没有扩大 Hook 的替代路径。后续停用与重启由用户确认，不修改认证缓存、框架数据库或其他模块状态。
