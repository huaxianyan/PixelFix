<div align="center">
  <img src="docs/images/icon.svg" width="120" alt="Pixel Fix 图标">

  # Pixel Fix
</div>

收集 Pixel 系统界面上的小问题修复，做成一个 LSPosed 模块。每个修复只在已核对的系统版本上生效，核对不上就只停用对应的那一项。

模块没有设置界面，也没有桌面入口，在 LSPosed 管理器里启用即可。

## 当前修复

| 修复 | 解决的问题 | 影响的系统版本 |
| --- | --- | --- |
| 屏保锁屏 | 退出时钟屏保后设备再次锁定，只能输入 PIN | Android 17（`CP3A.261005.005`） |

每个修复的核对方式、验收记录与已知边界见 [兼容范围](docs/COMPATIBILITY.md)。

## 快速开始

前置条件：Android 17 设备、已安装 LSPosed，且系统版本在对应修复的支持范围内。

1. 下载 `PixelFix-<版本>.apk` 并安装。
2. 在 LSPosed 管理器中启用「Pixel Fix」，作用域勾选「系统界面」。
3. 重启设备。

日志里出现 `adaptation matched` 说明核对通过并已启用；出现 `unavailable` 或 `disabled` 说明该系统版本不匹配，修复会自动停用。

从源码构建需要 JDK 17 与 Android SDK 37：

```bash
./gradlew testDebugUnitTest assembleDebug
```

产物在 `.work/build/app/outputs/apk/debug/`。

## 设计与实现

| 选择 | 原因 |
| --- | --- |
| 一个模块装多个修复 | 修复都针对系统界面，放在一起便于启用与停用 |
| 逐修复核对构建号与系统界面 APK 哈希 | 系统更新后符号和时序可能变化，核对不上就停用该项 |
| 只 Hook 已定位的原生入口 | 不扩大兜底范围，避免影响其他过渡与场景 |
| 无设置界面 | 目前没有需要用户现场调整的项 |

目录结构：

```text
app/src/main/java/com/neko7ina/pixelfix/   模块入口与各修复实现
app/src/main/resources/META-INF/xposed/    模块元数据、作用域、入口声明
docs/                                      兼容范围、发布说明、修复记录
```

## 文档

- [兼容范围](docs/COMPATIBILITY.md)：每个修复影响的系统版本、核对依据与停用条件。
- [发布说明规范](docs/RELEASE.md)：版本如何标注对应的问题与系统版本。
- [屏保锁屏修复：证据与设计](docs/fixes/dream-lockscreen/INVESTIGATION.md)。
- [屏保锁屏修复：验收与回退](docs/fixes/dream-lockscreen/VALIDATION.md)。
- [构建与首轮安装](docs/fixes/dream-lockscreen/BUILD.md)。
- [开发进度](docs/STATUS.md)。

## 第三方资源

图标中的扳手图形来自 Material Icons，许可见 [第三方声明](THIRD_PARTY_NOTICES.md)。
