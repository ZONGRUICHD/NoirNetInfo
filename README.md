# NoirNetInfo

Material 3 安卓应用：查看本机 IPv4 / IPv6、Wi-Fi、蜂窝与 SIM、网卡接口和公网地址。

## 功能

- 当前连接类型（Wi-Fi / 蜂窝 / 以太网 / VPN）与链路能力
- 全部网卡的 IPv4 / IPv6（含前缀、链路本地 / ULA / 全球）
- Wi-Fi：SSID、BSSID、信号、速率、频段、标准、加密
- SIM / 蜂窝：多卡槽、运营商、MCC/MNC、网络制式、漫游
- 公网 IPv4 / IPv6（ipify）
- 一键复制单项或全部信息

## 权限

| 权限 | 用途 |
| --- | --- |
| 网络状态 / Wi-Fi 状态 | 读取连接与地址 |
| 互联网 | 查询公网 IP |
| 精确位置 | 读取当前 Wi-Fi SSID（系统限制） |
| 电话状态 | 读取 SIM / 运营商信息 |

未授权时仍会展示其余可公开读取的信息。

## 从 Release 安装

每次推送到 `main`（或手动触发 Action）会构建 release APK，并发布到 [Releases](https://github.com/ZONGRUICHD/NoirNetInfo/releases)。

APK 使用 debug 密钥签名，便于 CI 产出可安装包；正式上架请自行替换正式 keystore。

## 本地构建

需要 JDK 17 与 Android SDK（compileSdk 35）。

```bash
./gradlew assembleRelease
```

产物：`app/build/outputs/apk/release/app-release.apk`
