# 时钟屏保退出后的锁屏状态分裂

> 本文件自本地 Android 问题工作区迁移，记录的是迁移前「屏保锁屏修复」独立模块时期的调查与验收。文中的本地目录（`.work/...`）与旧包名属于迁移前状态，现包名为 `com.neko7ina.pixelfix`，版本见 [开发进度](../../STATUS.md)。

## 问题与环境

双击退出充电时钟屏保后，桌面短暂出现。稍后设备重新锁定，指纹无法解锁，只能输入 PIN。当前取证构建为 Android 17 `CP3A.261005.005`，SystemUI 来自 `SystemUIGoogle.apk`。

时钟属于 Google `EverydayClockDreamService`，`canDoze=false`，不是这次调查最初误认的 AOD。停用所有 LSPosed 模块并重启后仍复现。此对照排除了模块加载依赖，未排除历史持久设置或其他系统修改。

## 已确认的故障时序

| 时间 | 事件 |
| --- | --- |
| 01:22:49.611 | 屏保宽限许可置为有效 |
| 01:22:49.615 | 调度 5000 ms 延迟锁定，seq=11 |
| 01:22:51.161 | 场景从 dream 转为 lockscreen |
| 01:22:51.492 | 人脸成功，来源为 FaceWithoutBypass，模式为 NONE_UNLOCKED |
| 01:22:51.493 | 窗口可见性分支要求隐藏锁屏 |
| 01:22:52.235 | 场景稳定在 lockscreen，没有进入 gone |
| 01:22:54.637 | 计时到期，直接返回桌面的许可撤销 |
| 01:22:54.638 | 以 lock screen timeout elapsed 为由再次锁定 |
| 01:22:54.642 | 指纹不监听，canSkipBouncer 仍为 true |

取证时框架确认 `10 installed, 0 enabled user states`。旧认证缓存仍显示生物识别已通过，TrustManager 报告 `deviceLocked=0`。强认证要求和指纹锁定均未出现。用户本轮尝试很多次才复现，没有准确次数，不能据此比较概率。

## 已定位的实现

`KeyguardWakeDirectlyToGoneInteractor` 在延迟锁定状态为 RUNNING 时，设置 `canIgnoreAuthAndReturnToGone=true`，ELAPSED 时设为 false。其场景清理过滤器排除了起点或终点为 Lockscreen 的过渡，本次 Dream → Lockscreen 没有清除此许可。

`canWakeDirectlyToGone` 要求设备已解锁，再判断是否满足直接进入条件。人脸通过使设备已解锁，残留的宽限许可使直接进入条件成立。

`WindowManagerLockscreenVisibilityInteractor` 优先按上述判断隐藏锁屏，日志原文为 `can wake directly to gone, LS must never show`，随后调用 `ATMS#setLockScreenShown(false, false)`。这是本次隐藏指令的直接来源，不能再归因于 AL 动画。

`LockAfterDelayInteractor` 通过「已进入设备」或清醒状态事件停止计时。认证通过并不等于已进入设备。本次场景仍在锁屏，没有停止旧计时。

`KeyguardUpdateMonitor.shouldListenForFingerprint` 对屏下指纹检查 `canSkipBouncer`。旧人脸认证仍有效，使新设备状态重锁后指纹继续停听。

## 正常对照

同一份现场中，01:22:43.579 同样出现 FaceWithoutBypass、NONE_UNLOCKED，但锁屏继续显示。45.364 用户点击进入图标后，才从 lockscreen 转入 gone。不能把人脸成功当作无条件直接进入桌面的授权。

## 原生屏保结束入口

2026-10-21 用本地 DEX 补全了调用关系，没有新增设备取材：

1. R8 合并后的 `SceneContainerStartable$handlePowerState$1$1.emit` 处理遮挡结束。当现有场景为 Dream 时，原因写为 `Dream stopped`。
2. 原生代码先读取 `canWakeDirectlyToGone` 与 `isDeviceEntered`。任一成立时选 Gone。否则保留适用的 Communal，或选择 Lockscreen。
3. `switchToScene$default` 把原生选择传给 `LegacySceneInteractorImpl.changeScene`。
4. `changeScene` 解析场景族，再调用 `validateSceneChange(from, to, reason)`。只有返回 true，才继续记录场景变化并调用场景仓库的 `changeScene`。`snapToScene` 也复用此校验。

因此，选中的修复入口是原生校验返回 true 之后、场景发布之前。匹配必须同时满足 Dream → Lockscreen 和原生原因 `Dream stopped`，不更改返回值。原生选择 Gone 或 Communal 时保持原样。这里依赖的是当前 APK 的调用语义，不把场景名称本身当作充分依据。

## 状态所有者与线程

- 计时所有者是 `LockAfterDelayInteractor`。构造函数注入的 `repository` 与 `sceneInteractor` 属于当前对象图。模块在构造完成后保存对象，命中时校对场景对象身份。
- `KeyguardRepositoryImpl` 保存 `lockAfterDelayState` 和 `_canIgnoreAuthAndReturnToGone`。后者是 SystemUI 的原生可写状态流，不是模块另建的许可。
- `ensureTimerStopped` 把状态设为 INACTIVE。此前为 RUNNING 时，还把 `timeoutCounter` 递增一次。`scheduleAlarm` 在调用可挂起的 `lockDelay` 前，已把序号写入 PendingIntent。因此即使设置读取稍后恢复，旧闹钟仍带旧序号。广播接收器只接受与当前序号一致的闹钟。
- 原生许可收集器仅对 RUNNING 和 ELAPSED 写值，忽略 INACTIVE。因此停止计时后必须另行撤销许可，不能指望状态流自动清理。
- 闹钟接收器注册时传入 null Handler，按 Android 语义在主线程接收。场景收集器使用 applicationScope，启动处没有覆盖调度器。模块要求实际回调运行在主 Looper，同步完成处理，不另行投递。若线程不符，记录错误并停止本进程的修复。

新增局部反汇编为 `.work/dream-lockscreen/sysui-dream-entry-disasm.txt` 和 `sysui-dream-owner-disasm.txt`。原生分支、校验先后顺序和接收器序号检查均有字节码依据。0.1.0 首轮已确认实际回调为主线程，计时所有者捕获成功。

## 0.1.0 的最小补丁

独立模块源码位于 `issues/dream-lockscreen/app/`，使用 libxposed API 102，仅作用于 SystemUI。运行时同时核对系统构建与 SystemUI APK 的 SHA-256，再核对精确的字段、方法和构造签名。没有其他构建或签名的替代入口。

模块只有一个改变状态的入口。原生校验通过并匹配上述屏保退出请求后，还要求设备清醒、计时为 RUNNING、直接返回许可仍有效。随后同步调用原生 `ensureTimerStopped`，再把原生许可设为 false。日志检查 INACTIVE、许可 false 和闹钟序号递增结果。构造 Hook 仅保存计时所有者，不修改原生状态。

正常返回桌面、Communal、宽限期已到期、主动熄屏和普通唤醒均不按此路径处理。对原生两个校验调用者取消编译优化，避免调用内联绕过 Hook。适配、Hook 安装、实际命中和处理结果分别记录。

这份实现未通过首轮完整现场验收。设备加载、构造捕获、主线程命中和当次宽限清理已验证，但随后仍出现人脸成功、锁头关闭、上滑要求 PIN 的状态分裂，详见下节。没有认证、指纹监听、窗口可见性或强制场景跳转 Hook，也未改变人脸设置。

## 0.1.0 首轮现场结果

用户自行启用模块并重启，随后报告屏保退出后仍要求 PIN，输入前保留了现场。助手只读取证，未输入 PIN、修改状态或重启。

| 时间 | 证据与结论 |
| --- | --- |
| 11:09:10 | 模块适配、Hook 安装和计时所有者捕获成功，框架 API 102 |
| 11:11:43.785 | 原生调度 5000 ms 闹钟，seq=1 |
| 11:11:45.797 | 补丁在主线程命中，INACTIVE、许可 false、序号 1 → 2 |
| 11:11:46.166 | 人脸通过，设备进入状态变为已解锁，场景保留锁屏 |
| 11:11:52.188 | 从锁屏再次进入屏保，没有新增活动宽限计时记录 |
| 11:11:53.194 | 原生日志报告 `doKeyguardTimeout` 锁定，不能归因为已取消的 seq=1 闹钟 |
| 11:11:53.195 | 设备进入状态变为锁定，但生物识别模式仍为 NONE_UNLOCKED |
| 11:11:55.124 | 再次退出屏保，模块因没有活动宽限而跳过 |
| 11:11:55.508 | 人脸再次认证成功，随后仍写入 MODE_NONE_UNLOCKED，未观察到设备进入状态恢复解锁 |
| 11:12:00.447 | 上滑显示密码面板。用户确认锁头仍关闭，尚未输入 PIN |

保留现场的快照显示 `getUserUnlockedWithBiometric()=true`、`canSkipBouncer=true`、指纹实际和预期监听均为 0。强认证标志为 0，指纹没有锁定。TrustManager 报告 `deviceLocked=0`，与锁头、密码面板和设备进入状态不一致。不能把认证成功文案当作新设备状态已正确解锁。

因此，旧闹钟清理只获得局部验证，`0.1.0` 不能交付为完整修复。取证文件为 `.work/dream-lockscreen/pin-held-{logcat,buffers,systemui,trust,module}.txt`。较早的 `first-test-events.txt` 和 `first-test-auth.txt` 补充时序，不能与全模块停用现场混用。

## 下一处断点：重复认证的状态传播

现有 DEX 进一步确认：普通模式下，`DeviceUnlockedInteractor.deviceUnlockSource` 的生物识别来源取自 `BiometricUnlockInteractor.unlockState`，先映射 `BiometricUnlockModel`，不是直接消费每次人脸成功回调。`setBiometricUnlockState` 创建模式、来源和用户组成的模型，写入仓库的 StateFlow。

现场重新锁定时仍保留 NONE_UNLOCKED，第二次人脸成功又报告同一模式。重复模型被 StateFlow 等值合并、导致新的成功未传播到设备进入状态，是目前优先核对的假设。尚需确认模型等值实现、完整的模式重置调用链，以及该次重锁来源，不能仅凭日志相邻宣布完整因果成立。

下一步先查 `BiometricUnlockController` 的原生模式重置与屏保／锁定生命周期。修复若需调整，必须通过正确的原生状态所有者结束旧认证周期，保留后续真实认证和用户进入动作。禁止直接把设备状态设为已解锁、重放旧认证结果、放行密码面板或强制开启指纹。

新增局部反汇编为 `sysui-auth-propagation-disasm.txt` 与 `sysui-auth-source-disasm.txt`，仍只存取证容器。当次只读调查没有新增产品代码、构建、安装或模块开关操作。

## 0.2.0：在下一轮屏保开始时结束旧模式

继续核对取证 DEX 后，确认 `BiometricUnlockModel.equals` 只比较模式、来源和用户。`StateFlowImpl.updateState` 发现模型相等便直接返回，不通知订阅者。这解释了重复认证模型无法再次传播的机制。

`BiometricUnlockController.resetMode()` 是模式的原生重置入口。它将仓库模型设为当前用户的 NONE、来源 null，同时重置控制器的模式、来源和原生附属状态。NONE 映射为空解锁来源，随后由 `filterNotNull` 丢弃，不产生解锁事件。新一次真实认证再写入人脸成功模型，才会形成新的模式变化。

取证包中，控制器的直接重置调用方包括开始休眠、密码面板状态变化、用户切换和旧锁屏隐藏流程。`KeyguardService.onDreamingStarted` 只更新屏保状态、通知监视器并按锁定超时处理，没有模式重置。这与现场中屏保跨轮保留 NONE_UNLOCKED 一致。`doKeyguardTimeout` 从 KeyguardService Binder 入口进入 `tryLockNow`，助手没有拦截或屏蔽该锁定请求；其远端完整调用链仍未取证。

新实现保留同一个 `validateSceneChange` 运行时修复入口，增加原生接受 `Dream started`、目标为 Dream 的分支。在发布新屏保场景前，满足以下条件才调用 `resetMode()`：

- 当前处于清醒状态。
- 控制器模式为取证 APK 中的 8，即 MODE_NONE_UNLOCKED。
- 仓库模型也是 NONE_UNLOCKED，来源为 FACE_SENSOR。
- 模型用户与当前选中用户一致。
- 控制器与计时所有者属于同一仓库和场景实例。

此时设备进入状态可能仍是上一轮人脸通过后的已解锁，不能把「已经锁定」作为前提，否则会漏掉现场的锁屏再次进入屏保路径。补丁只结束旧模式，由系统照常决定锁定、等待下一次真实认证和用户进入动作。不写设备解锁结果，不重放认证，不清理或伪造生物识别成功缓存，也不修改指纹监听条件。

为取得实际控制器，新增其精确构造器捕获，延迟到生命周期事件才求值场景与用户依赖。没有 Hook 认证成功回调或模型写入方法。字段、方法和构造器签名逐项与取证 DEX 核对，明确使用 FACE_SENSOR，不用候选枚举兜底。

原有屏保结束时的活动宽限清理保持原样。两个生命周期分支共用构建、APK 哈希、场景归属和主线程校验，错误后禁用修复，不新增替代路径。

本地 `0.2.0` 构建、2 条条件测试和 APK 签名检查通过。开发阶段没有 ADB 或设备变更。随后按当次授权安装，由用户重启并复测，运行时结果见下节。包路径与验收步骤见 [构建文档](BUILD.md)。

本轮额外反汇编：`sysui-reset-lifecycle-disasm.txt`、`sysui-reset-callers-disasm.txt` 和 `sysui-dream-auth-cycle-disasm.txt`，均存于 `.work/dream-lockscreen/`。

## 0.2.0 本轮现场复测

用户报告尝试数次，没有再触发故障，没有准确测试次数。助手只读保存模块日志与 SystemUI 缓冲区，未改设置、更新模块或重启。

新模块确实执行了修复：日志包含 7 次旧人脸模式重置、4 次活动宽限清理。事件数量不等于完整测试次数。代表性的同路径对照如下：

| 时间 | 事件 |
| --- | --- |
| 11:53:11.461 | 新屏保开始，将当前用户旧人脸模式从 NONE_UNLOCKED / FACE_SENSOR 重置为 NONE / null |
| 11:53:12.465 | 系统重新锁定，设备进入状态为 false |
| 11:53:14.093 | 新一次真实人脸认证成功 |
| 11:53:14.097 | 设备进入状态恢复为 true |
| 11:53:17.435 | 用户操作从锁屏进入桌面 |

另一轮在 11:53:21.722 清理活动宽限，序号 4 → 5，22.100 人脸认证成功，22.105 恢复设备进入状态，26.114 用户操作进入桌面。本轮没有出现 0.1.0 中第二次人脸成功却停留在锁定设备进入状态的情况。

证据为 `.work/dream-lockscreen/0.2.0-first-test-module.txt` 与 `0.2.0-first-test-buffers.txt`。结论是本轮复测通过，新增重置确实执行，认证传播与用户进入恢复。尚无长期复现率结论，完整回归表也未逐项验收。当前保持用户日常状态观察，不继续为补次数重复测试。

## 原始材料

本地材料统一在根目录 `.work/dream-lockscreen/`。以 `all-off-held-` 开头的是全模块停用现场，`sysui-` 开头的是 DEX 与相关反汇编。材料保留为现场证据，不加入 Git。调查最初来自 ActionLauncherEnhanced 的 STATUS-006 至 STATUS-008，后续结论在本工作区维护。
