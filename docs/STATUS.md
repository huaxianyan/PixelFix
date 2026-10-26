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
