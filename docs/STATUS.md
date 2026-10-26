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

## 后续计划

1. 按当次授权在设备上卸载旧模块并安装，确认作用域、核对日志与屏保场景行为。
2. 接入下一项修复（锁屏退出音效重复）时，按同一套规则独立核对构建。
