# Gozero Pearl Android

在 ARM64 安卓手机上运行的 Pearl（PRL）挖矿应用，提供 CPU 核心及可选 GPU 辅助。

**当前版本：1.0** · Android 9 及以上 · 包名 `trade.gozero.pearl`

[下载 APK](https://github.com/AlexZeroZero/gozero-pearl-android/releases/latest) · [Pearl 官网](https://pearlresearch.ai/) · [开发记录](CHANGELOG.md)

## 使用

1. 从 Releases 下载 `Gozero-Pearl-1.0-arm64-v8a.apk` 并安装。已有 Gozero Pearl 用户可覆盖升级，保留设备配置。
2. 在「配置」中填写自己的 PRL 公开收款地址，选择矿池、矿工名、线程和矩阵配置。应用不需要私钥或助记词，源码默认收款地址为空。
3. 点击「启动挖矿」。首次使用计算核心需要通过本机证明自检；首页显示算力、实际线程、内存、温度和矿池状态。

## 功能

- ARM64 CPU 计算、SIMD / I8MM 加速、V3 证明生成与验证。
- 可选 OpenCL / Vulkan GPU 辅助，按已测试的 Snapdragon 机型选择路径；GPU 支持取决于设备驱动。
- 可配置线程与矩阵尺寸，SM8650 自动模式支持按核心容量分组及动态工作节奏，修复旧版长期锁定低负载缓存的问题。
- CPU、GPU、电池及 Android 系统温控；降温满足恢复条件后自动继续。
- LuckyPool / Kryptex 挖矿配置与余额查询。PearlHash 目前仅支持数据查询，其专有挖矿协议尚未兼容。
- 中文、英语、俄语、日语；深色 / 浅色主题、前台通知、迷你算力显示及日志。

## 1.0 发布范围与实测

1.0 基于已验证的 0.9.1 版本，仅更新发布版本号、界面文字及客户端版本标识。CPU / GPU 原生二进制、计算算法和温控逻辑与该版本相同。

上一轮 S24 Ultra SM-S9280（Snapdragon 8 Gen 3）、自然散热、7 线程、1K × 128K 矩阵，约 596 秒观察：

| 项目 | 结果 |
| --- | --- |
| CPU 累计工作量 / 整段时间 | 299.62 GH/s |
| CPU + GPU 已采集工作量 / 整段时间 | 至少 302.16 GH/s |
| CPU 矿池接受 / 拒绝 | 1 / 0 |
| 末 3 分钟 CPU 平均 | 282.13 GH/s |

上述平均包含启动、准备和温控暂停。暂停进程不查询统计接口，因此 GPU 采用最后已确认的累计工作量，不把暂停前速率外推为持续算力。GH/s 在本应用中对应 Pearl 矩阵运算 GMAC/s。

仍存在温停；没有证明达到下午旧版的持续水平或超过 GT5。GPU 通过本机 V3 自检，本轮没有捕获 GPU 独立矿池接受份额。设备、散热、系统调度与矿池任务均会影响结果。版本定为 1.0 不代表所有机型性能已验证。

本次发布验证见 [validation-1.0.json](docs/validation-1.0.json)。历史长测属于 0.9.1，不作为重新测得的 1.0 性能结果。

## 构建

需要 Java 17、Android SDK 35、Gradle 8.11.1（AGP 8.9.2）和 Python 3。通过 `ANDROID_HOME` 或本地 `local.properties` 指定 SDK。

```text
python tools/build.py
```

默认生成调试 APK，并执行 JVM 测试和 Android lint。仓库包含与发布版一致的 ARM64 原生核心；仅编译 Android 界面不需要重新构建 Rust / C++。

重新构建 CPU 核心还需 NDK 28.2、CMake 3.22.1、Ninja、Rust 1.93.0 和 `aarch64-linux-android` target，设置 `ANDROID_NDK_HOME` 后运行：

```text
python tools/build.py --native
```

重新构建 GPU 还需设置 `OPENCL_INCLUDE_DIR`（Khronos OpenCL-Headers）和 `OPENCL_LIBRARY`（ARM64 OpenCL 链接库），运行 `python tools/build.py --gpu`。链接库仅供构建，不应作为厂商驱动打包进 APK。脚本会生成 Vulkan 着色器并更新核心校验摘要。

发布签名通过环境变量 `BNT_KEYSTORE`、`BNT_STORE_PASSWORD` 提供，密钥别名为 `gozero-bnt`，然后运行 `python tools/build.py --release`。这些名称沿用既有构建配置；仓库不包含发布签名密钥。自行签名的 APK 不能覆盖由其他签名签署的已安装版本。

## 源码与许可

Android 集成及优化由 Gozero 开发。原生核心基于 [CPPminer](https://github.com/1640675651/CPPminer)，源提交 `5b68506af5e6433ee5c25aa9e6fbb00b537590aa`，保留其 MIT 许可；证明库及其他依赖保留各自许可。完整声明见 [LICENSES.txt](app/src/main/assets/LICENSES.txt) 和 `native/cppminer` 内的许可文件。

发布内容包含应用源码、原生核心及证明库源码、构建工具、依赖许可和验证摘要。设备日志、个人配置、签名密钥和本机工具链路径不属于公开发布内容。
