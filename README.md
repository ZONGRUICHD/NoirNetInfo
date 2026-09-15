# NoirNetInfo

Material 3 安卓应用：查看本机 IPv4 / IPv6、Wi-Fi、蜂窝与 SIM、网卡接口和公网地址。

## 功能

- 左侧分类栏：概览 / 地址 / 公网 / 接口 / Wi-Fi / 蜂窝 / 各 SIM 卡槽
- 当前连接类型（Wi-Fi / 蜂窝 / 以太网 / VPN）与链路能力
- 全部网卡的 IPv4 / IPv6（含前缀、链路本地 / ULA / 全球）
- Wi-Fi：SSID、BSSID、信号、速率、频段、标准、加密
- SIM / 蜂窝（参考 Cellular-Z 卡槽层级）：
  - 双卡卡槽、默认数据/通话/短信、eSIM、ICCID
  - 运营商、PLMN、注册状态、5G NSA/SA、载波聚合（设备未提供时显示未知）
  - 服务小区 + 邻区：LTE TAC/PCI/ECI/RSRP/RSRQ/SINR/EARFCN/Band
  - GSM / WCDMA / TD-SCDMA / CDMA / NR 5G 对应标识与无线测量
  - 前台每 3 秒刷新网络快照；小区显示系统缓存的实际测量时间，后台停止轮询
  - **Shizuku 锁网**：尝试设置制式（Android 13+）、Band、频点；是否支持取决于系统和基带。PCI 尚未进行设备适配，禁用写入。
- 公网 IPv4 / IPv6（ipify）
- 一键复制单项或全部信息

## 权限

| 权限 | 用途 |
| --- | --- |
| 网络状态 / Wi-Fi 状态 | 读取连接与地址 |
| 互联网 | 查询公网 IP |
| 精确位置 | 读取 Wi-Fi 名称、服务小区与邻区 |
| 电话状态 | 读取 SIM / 运营商 / 制式 |

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

## 数据口径与限制

- 精确定位权限和系统定位开关是两个独立条件；只授予大致位置不能保证读取小区。
- `getAllCellInfo()` 返回系统缓存。页面刷新时间不代表基带进行了新测量；邻区是否返回由设备决定。
- NR 优先显示系统报告的频段；仅凭 NR-ARFCN 推导时列出候选并标注推测，不能区分 n77/n78 等重叠频段。
- SA 以数据制式 NR 判断；5G 图标、NR Advanced 标识不能单独证明 SA 或 NSA 已连接。
- 公网查询分别使用 ipify IPv4 / IPv6 专用接口，出口可能经过 VPN。查询失败不代表设备不支持该协议。
- 本项目尚未实现 Cellular-Z Pro 的全部功能，尤其是基带专有诊断、层三信令和机型专用锁 PCI。

## 验证

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug assembleRelease
```
