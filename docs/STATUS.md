# 开发进度

## 当前主线

仓库从本地 Android 工作区的 `issues/dream-lockscreen`（「屏保锁屏修复」0.2.0）迁移而来，合并为单模块 Pixel Fix `0.3.0`，包名改为 `com.neko7ina.pixelfix`。屏保锁屏修复代码原样迁移，仅改包名与构建核对常量名；脚本迁移后单元测试与 Debug 构建通过。尚未安装到设备，也未发布 Release。

## STATUS-001：建立 Pixel Fix 仓库并迁移首个修复

- 日期：2026-10-26。用户决定把系统侧修复集中在单个模块，仓库名 `PixelFix`，模块名「Pixel Fix」。
- 迁移内容：`MainModule`、`NativeAccess`、单元测试、系统界面作用域声明、模块元数据，以及原调查、验收、构建三份文档。
- 命名变化：包名 `com.neko7ina.pixelfix`；核对常量改为 `DREAM_LOCKS_BUILD` 与 `DREAM_LOCKS_SYSTEMUI_SHA256`，为后续修复各自独立核对留位。
- 版本：`0.3.0 / 3`，接续原「屏保锁屏修复」0.2.0 的功能。
- 图标：自适应图标，蓝色背景配白色充电底座、充电弧线与扳手，附单色变体。
- 验证：`testDebugUnitTest assembleDebug` 通过（2 条测试），Debug 包 38671 字节，包名与标签经 `aapt2` 核对。
- 未完成：真机安装与验收（更换包名需要卸载旧模块）、GitHub Release、后续修复接入。

## STATUS-010：合并到 main 并发布 v0.4.0

- 日期：2026-10-27。分支 `fix/keyguard-double-sound` 合并到 `main`，版本升到 `0.4.0 / 4`。
- 发布说明写入 `docs/release-notes/v0.4.0.md`，按要求单独写明适用系统版本与构建号。
  发布说明门禁脚本校验通过。
- 产物：`PixelFix-0.4.0.apk` 与同名 `.sha256`，使用正式签名身份；已发布
  https://github.com/huaxianyan/PixelFix/releases/tag/v0.4.0 ，线上资产下载后校验一致。

## STATUS-009：静音根因确认为原生未请求，补声后真机通过

- 日期：2026-10-27。两份静音现场（14:44:14、14:44:31）确认：解锁完成、keyguardDone
  跑了两次，但整段没有 `exitKeyguardAndFinishSurfaceBehindRemoteAnimation`，
  音频服务也没有任何系统界面音效启动——即原生根本没请求播放，模块无从静音。
- 同类静音在钩子完全未生效的版本里也出现过，进一步排除模块因素。
- 处理：保留「重复请求丢弃」，另加「补声」——收到 USER_PRESENT 时若过去 500 毫秒内
  没有解锁音，就通过原生 `playSound` 补播一次。
- 真机（SystemUI 27694）：用户连续解锁约 20 次，全部有声音、没有两声、无感知延迟。
  日志：跳过 7 次（间隔 400～408 毫秒，另有 1 次 6 毫秒），补声 1 次（间隔 2939 毫秒）。
- 待办：合并到 main、提升版本号；诊断工具的模块日志过滤仍需加入 PixelFix。

## STATUS-008：改在音效池播放处拦截，已确认接线成功

- 日期：2026-10-27。按用户选择改为拦截 `android.media.SoundPool.play`，
  只匹配锁屏自己持有的音效池实例与 `mUnlockSoundId`，其他音效一律放行。
- 关键坑：`mLockSounds` 与 `mUnlockSoundId` 在构造器里还是 null/0，要到
  `setupLocked()` 之后才有值；改为首次播放时惰性读取。日志确认
  `Keyguard sound owner captured; unlockId=2`。
- 现状：日志出现 `Keyguard sound playback observed`，说明钩子真正命中了解锁音。
  当前进程内尚未遇到重复，因此还没有跳过记录。
- 仍待验证：真实重复出现时是否被正确压掉，以及正常解锁是否始终保留一声。

## STATUS-007：退出钩子仍被 JIT 绕过，修复判定为不可靠

- 日期：2026-10-27。探针记录（每次 `onKeyguardExitFinished` 一行）显示：
  同一段时间内实际播放 7 次解锁音，钩子只收到 3 次调用，且三次都是
  `showing=true`、不判定为重复。跳过计数为 0。
- 结论一：即便对四个调用方都做了 `deoptimize`，调用仍会被 JIT 重新内联绕过，
  修改后钩子只能偶然命中。此前的 `playSound` 钩子也是同一原因。
- 结论二：USER_PRESENT 并非只由 `onKeyguardExitFinished` 发出，
  `DeviceEntryUserPresentStartable` 也会发；因此「广播条数」不能当作退出次数使用，
  早期用它推算重复次数的方法作废。
- 现状：该修复在真机上基本不生效，也未造成副作用。探针日志已移除。
- 待定：要么放弃音效去重只保留屏保修复，要么改在更底层（如 SoundPool）拦截，
  后者影响面更大、需重新评估。

## STATUS-006：去重规则改为时间窗加锁屏隐藏双条件

- 日期：2026-10-27。探针日志证实：解锁音 ID=2、锁屏音 ID=1，两者不同音效。
  所有正常解锁请求都发生在 `mShowing=true` 时，唯一被跳过的重复是 `mShowing=false`
  且距上一次解锁音 8 ms；另有一次 `mShowing=false`、939 ms 的请求应当播放。
- 结论：单看时间窗会误杀「解锁→锁屏→再解锁」，必须同时要求锁屏已隐藏。
- 规则：请求的是解锁音、且 `mShowing=false`、且距上次解锁音 < 800 ms 才跳过。
  三者任一不满足即原样播放；锁屏音与解锁广播始终不受影响。
- 探针日志已移除；单元测试按上述四种情形覆盖。`assembleRelease` 通过并覆盖安装，
  SystemUI 重启后钩子与新规则已加载。

## STATUS-005：音效去重改为调用方反优化与时间窗判定

- 日期：2026-10-27。真机首轮：5 次解锁里 2 次没有音效，但模块的跳过计数为 0，
  说明钩子根本没被调用——`playSound` 的调用方 `onKeyguardExitFinished` 被 AOT 内联，
  与屏保修复当初遇到的情况一致。那两次静音属原生行为，不是模块造成的。
- 修正一：安装时对 `onKeyguardExitFinished` 调用 `deoptimize`，让 `playSound` 钩子生效。
- 修正二：判定条件从 `mShowing` 换成时间窗——同一把解锁里 800 ms 内的第二次解锁音才跳过
  （实测重复间隔 56～444 ms）。没有前一声时永远播，因此不会再出现整次静音。
- 新增首次命中日志 `Keyguard sound interceptor active`，用于确认钩子确实被调用。
- 验证：3 条单元测试与 `assembleRelease` 通过；已覆盖安装，但 SystemUI 进程仍是旧代码，
  需重启后生效。

## STATUS-004：建立正式签名身份并首次安装

- 日期：2026-10-27。按用户选择生成随机口令的 PKCS#12 密钥库，RSA 4096，有效期 100 年。
  口令写在本地 `signing/signing-credentials.txt`，未在对话中显示，也未进 Git。
- 证书主题 `CN=Pixel Fix, OU=Release, O=huaxianyan, C=CN`，SHA-256
  `48cda999ed4dcc8b86c297c949f319b977cfa98ffd3c1bce70bad95ba72cc876`；公开证书
  提交为 `signing/release-certificate.pem`，说明见 `SIGNING.md`。
- 构建：`app/build.gradle.kts` 从凭据文件读取签名材料，缺文件时 release 变体不签名。
  `assembleRelease` 产物 60589 字节，`apksigner` 证书与仓库基准证书一致。
- NAS 尚未上线，恢复副本待用户手动备份到约定目录。
- 安装：已按当次授权卸载 `com.neko7ina.dreamlockscreenfix`，安装正式签名包
  `com.neko7ina.pixelfix` 0.3.0 / 3（60589 字节）。设备上取回的已安装包证书与基准一致。
  模块启用与重启由用户自行完成。

## STATUS-003：实现锁屏音效去重，待真机验证

- 日期：2026-10-27。分支 `fix/keyguard-double-sound`，未合并、未发布。
- 五份现场结论：一次解锁里 `onKeyguardExitFinished` 会跑两次以上，每次都播放解锁音并
  发一次 USER_PRESENT。第一次在锁屏仍显示时播，收尾那次 `mShowing` 已为 false。
- 实现：新增 `KeyguardSoundFix` 与纯逻辑 `KeyguardSoundPolicy`，只 hook
  `KeyguardViewMediator.playSound(I)`，当请求的音效等于 `mUnlockSoundId` 且 `mShowing`
  为 false 时跳过。锁屏音、解锁广播、认证与场景判断都不动。
- 核对方式调整：两个修复针对同一个系统界面 APK，构建号与 APK 哈希收成一组
  `SYSTEMUI_BUILD` / `SYSTEMUI_SHA256`；各项修复仍各自捕获原生所有者并在失败时
  单独停用。原 `DREAM_LOCKS_*` 常量随之改名。
- 验证：`testDebugUnitTest assembleDebug` 通过（3 条测试，含新增策略测试）。
  未安装、未真机验证；第二次广播是否被消费不再需要关心，因为广播保持原样。
- 待办：真机确认只响一声且解锁广播仍为两次；合并到 main 与版本号提升另定。

## STATUS-002：图标定为维护者微调稿

- 日期：2026-10-26。维护者提供微调后的 PNG，图案以此为准：白色机身、深色相机条与镜头点、
  空心描边扳手压住机身右下角并露出边缘。
- 处理方式：抠掉底色后按自适应图标规范居中，导出五档密度的
  `drawable-*/ic_launcher_foreground.png`；背景改用纯色 `#2E4257`。
  旧的矢量前景、单色矢量与 `docs/images/icon.svg` 删除，README 改用 `docs/images/icon.png`。
- 核对：圆形与圆角方形遮罩下内容均在安全区内；`aapt2` 确认图标解析为
  `res/mipmap-anydpi-v33/ic_launcher.xml`（minSdk 37，v26 配置被 v33 覆盖，属预期）。
- 构建通过，APK 40158 字节；未安装到设备。

## 后续计划

1. 按当次授权在设备上卸载旧模块并安装，确认作用域、核对日志与屏保场景行为。
2. 接入下一项修复（锁屏退出音效重复）时，按同一套规则独立核对构建。
