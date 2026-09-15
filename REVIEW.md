# NoirNetInfo 审查与修复（2026-09-15）

## 审查依据

检查本地仓库（起点 `f2d8fd0`）、README、构建发布工作流，以及本机该项目 Cursor 的对话记录 `80117047-142d-46c4-965e-3386dba7d93f`。已阅读用户需求与助手交付说明；该记录没有可靠的模型身份标注，不能单独确认是否为 Grok。

保留原有 Material 3、Logo、左侧分类和每张 SIM 独立页面。

## 已修复

| 优先级 | 原问题 | 修复后 |
| --- | --- | --- |
| P1 | PCI 操作直接向多个 `/dev/smd*` 节点发送未经适配的 AT 指令；写入成功就当作锁定成功 | 停用 PCI 写入，界面和服务端均说明尚未适配；不将 Root 等同于 modem 能力 |
| P1 | 旧系统直接调用较新 Android API | 为 NR、TD-SCDMA、MTU、GSM BER、选网等补齐 API 边界；公开制式设置限定 Android 13+ |
| P1 | NR 优先匹配 n77 导致 n78 等频段误报 | 优先采用系统报告的 NR bands；换算只列候选并注明推测，保留 NR raster 精度 |
| P1 | WCDMA B3 被识别为 B5，多个下行频率偏移错误 | 修正 B3/B4/B5/B8 范围和换算；加入边界测试 |
| P1 | 5G Advanced 显示标识被当成 SA | SA 基于数据制式 NR；NSA 已连接依赖连接状态，显示标识单独列出 |
| P1 | IPv6 使用双栈查询，可能返回 IPv4 | 使用 IPv6 专用接口，校验两种协议响应，解释查询出口与失败含义 |
| P1 | 前后台持续采集，网络变化反复触发公网请求，旧网络结果可能回填 | 仅前台每 3 秒读取本地快照；公网由手动刷新/默认网络改变触发；取消旧请求并校验网络身份 |
| P2 | 信号表盘把实际值裁剪成进度条上下限 | 文本保留原始测量，只有进度条截断；非 LTE/NR 使用接收信号指标 |
| P2 | 默认数据卡硬取 SIM 列表第一项；主 IP 可能取其他接口 | 按 default-data 标识选择 SIM，概览主地址限定默认网络接口 |
| P2 | CGNAT、IPv6 特殊地址标为全球地址，同地址不同接口被去重 | 增加共享地址分类，按字节判断 IPv6，保留接口维度 |
| P2 | 大致位置被当作完整授权；缺少关闭系统定位的说明 | 精确定位单独判断；增加定位开关提示和应用授权设置入口 |
| P2 | 接收速率误用通用 linkSpeed | Android 10+ 使用 rxLinkSpeedMbps；低版本不冒充接收速率 |
| P2 | 把系统缓存当作实时测量 | 展示小区的系统测量时间，README 明确缓存口径 |
| P2 | 长字段固定标签宽度、三行截断、复制按钮偏小，折叠状态随副标题刷新重置 | 标签按宽度分配，值完整换行，48dp 复制触区并居中对齐，折叠状态不依赖动态副标题 |
| P2 | 页面切换保留另一页滚动位置；返回键直接退出 | 切换页面回到顶部；SIM 返回蜂窝，其他分类返回概览；蜂窝摘要可直接进入 SIM |
| P2 | 锁网表单自动填入频段/PCI/频点，频点制式由当前小区猜测 | 默认不填限制，手动选择 LTE/NR 频点制式，校验数字和启用的制式，禁用不支持的 PCI |
| P2 | 频点所属制式不在 Band 列表时请求遗漏 | 组合所有启用的制式和对应频段/频点，避免丢失频点约束或无意排除其他制式 |
| P2 | 缺失选网方法仍报告解除成功 | 缺少接口明确返回失败；Band/频点请求提交与实际生效区分 |
| P2 | 更新安装授权返回后不继续，可能重复下载 | 授权结果回调继续安装；保留已下载 APK 重试入口，防止重复下载/检查，校验 Content-Length |

## 验证与边界

- 最终 `assembleDebug testDebugUnitTest lintDebug assembleRelease` 全部成功；Lint 0 错误、75 警告（包含依赖更新、资源与旧 API 建议，详见构建报告）。
- 6 项 JVM 回归测试覆盖 LTE 零频点、NR 重叠候选与频率精度、WCDMA、GSM、CGNAT、IPv6 特殊地址。
- Android 15 模拟器：验证首次未授权仍显示基础信息、授予定位/电话权限后显示 Wi-Fi/SIM、侧栏进入 SIM、返回 SIM → 蜂窝 → 概览、320dp 窄屏与长标题。检查 AndroidRuntime 日志无应用崩溃。
- 未连接真实手机：双卡归属、NSA/SA、邻区更新速度、厂商隐藏 API、Shizuku 权限及实际锁网效果、完整系统安装升级流程都不能用模拟器代替验证。
- 模拟器冷启动出现过系统 UI / Messages 无响应弹窗，关闭弹窗后完成应用检查；这些不是 NoirNetInfo 崩溃证据。

## 仍需后续工程处理

1. **Cellular-Z Pro 功能并未全量对齐。** 尚无经过机型验证的 PCI/基带适配、层三信令、历史测量曲线。Shizuku 只提供身份权限，不能凭空补足基带接口。
2. **发布签名。** 现有 CI 使用 debug keystore；GitHub 新 runner 生成的密钥可能不同，已有用户可能无法覆盖安装。需要原发布密钥或制定新签名迁移方案；本次不替换用户签名。
3. **同版本重复发布。** CI tag 中的 run number 没有进入应用版本比较；同 versionName 的重复构建不会被更新检查识别。发布时需递增 versionCode/versionName，后续应统一 CI 与应用版本策略。
4. **数据来源限制。** 隐藏反射 API 在部分厂商不可用；`getAllCellInfo` 不保证最新测量。下一步适合接入按订阅的 TelephonyCallback / CellInfoCallback，并在真实双卡设备验证归属。
5. **界面结构。** 目前每页仍把多个信息卡放在单个 LazyColumn item 内；大量邻区时可进一步拆成有稳定 key 的懒加载条目。本次未重写整套页面架构。

## 核对资料

- [Android TelephonyDisplayInfo：显示标识语义](https://developer.android.com/reference/android/telephony/TelephonyDisplayInfo)
- [Android TelephonyManager：小区缓存、请求与制式接口](https://developer.android.com/reference/android/telephony/TelephonyManager)
- [ipify：IPv4、IPv6 与双栈接口](https://www.ipify.org/)
- [ETSI TS 125 101：UTRA 频率和 UARFCN](https://www.etsi.org/deliver/etsi_ts/125100_125199/125101/11.08.00_60/ts_125101v110800p.pdf)
