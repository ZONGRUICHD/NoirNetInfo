package com.zongruichd.noirnetinfo.shizuku

import android.content.ComponentName
import android.content.Context
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.telephony.AccessNetworkConstants
import com.zongruichd.noirnetinfo.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import rikka.shizuku.Shizuku

data class ShizukuUiState(
    val installed: Boolean = false,
    val running: Boolean = false,
    val permissionGranted: Boolean = false,
    val bound: Boolean = false,
    val uid: Int = -1,
    val lastResult: String? = null,
) {
    val mode: String
        get() = when {
            !installed -> "未安装"
            !running -> "未运行"
            !permissionGranted -> "未授权"
            uid == 0 -> "Root"
            uid == 2000 -> "ADB"
            uid > 0 -> "uid=$uid"
            else -> "已授权"
        }

    val ready: Boolean get() = running && permissionGranted && bound
}

class ShizukuController(private val context: Context) {
    private val _state = MutableStateFlow(ShizukuUiState(installed = isInstalled()))
    val state: StateFlow<ShizukuUiState> = _state.asStateFlow()

    private var service: IPrivilegedTelephony? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName(context.packageName, PrivilegedTelephonyService::class.java.name),
    )
        .daemon(false)
        .processNameSuffix("priv")
        .debuggable(BuildConfig.DEBUG)
        .version(BuildConfig.VERSION_CODE)
        .tag("noir-telephony")

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder?) {
            if (binder == null || !binder.pingBinder()) {
                service = null
                _state.update { it.copy(bound = false, lastResult = "UserService binder 无效") }
                return
            }
            service = IPrivilegedTelephony.Stub.asInterface(binder)
            val status = runCatching { service?.status() }.getOrNull()
            _state.update { it.copy(bound = true, lastResult = status) }
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            _state.update { it.copy(bound = false) }
        }
    }

    private val binderReceived = Shizuku.OnBinderReceivedListener { refresh() }
    private val binderDead = Shizuku.OnBinderDeadListener {
        service = null
        _state.update { it.copy(running = false, bound = false, permissionGranted = false, uid = -1) }
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, grantResult ->
        refresh()
        if (grantResult == PackageManager.PERMISSION_GRANTED) bind()
    }

    init {
        runCatching {
            Shizuku.addBinderReceivedListenerSticky(binderReceived)
            Shizuku.addBinderDeadListener(binderDead)
            Shizuku.addRequestPermissionResultListener(permissionResult)
        }
        refresh()
    }

    fun dispose() {
        runCatching { Shizuku.removeBinderReceivedListener(binderReceived) }
        runCatching { Shizuku.removeBinderDeadListener(binderDead) }
        runCatching { Shizuku.removeRequestPermissionResultListener(permissionResult) }
        runCatching { Shizuku.unbindUserService(userServiceArgs, connection, true) }
    }

    fun refresh() {
        val installed = isInstalled()
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val granted = running && runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val uid = if (running) runCatching { Shizuku.getUid() }.getOrDefault(-1) else -1
        _state.update {
            it.copy(installed = installed, running = running, permissionGranted = granted, uid = uid)
        }
        if (granted && !(_state.value.bound && service != null)) {
            bind()
        }
    }

    fun requestPermission() {
        if (!runCatching { Shizuku.pingBinder() }.getOrDefault(false)) {
            _state.update { it.copy(lastResult = "请先在 Shizuku 里启动服务") }
            return
        }
        if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
            bind()
            return
        }
        Shizuku.requestPermission(REQUEST_CODE)
    }

    fun applyLock(
        subId: Int,
        enableGsm: Boolean,
        enableWcdma: Boolean,
        enableLte: Boolean,
        enableNr: Boolean,
        lteBands: List<Int>,
        nrBands: List<Int>,
        wcdmaBands: List<Int>,
        arfcn: Int?,
        pci: Int?,
        servingRat: String?,
    ): String {
        val svc = service ?: return "Shizuku UserService 未连接"
        val mask = listOfNotNull(
            RatMask.GSM.takeIf { enableGsm },
            RatMask.WCDMA.takeIf { enableWcdma },
            RatMask.LTE.takeIf { enableLte },
            RatMask.NR.takeIf { enableNr },
        ).fold(0L) { acc, v -> acc or v }
        if (mask == 0L) return "至少选择一种制式"
        val messages = mutableListOf<String>()
        runCatching { svc.setAllowedNetworkTypes(subId, mask) }
            .onSuccess { messages += it }
            .onFailure { messages += "制式: ${it.message}" }

        val rans = mutableListOf<Int>()
        val counts = mutableListOf<Int>()
        val bands = mutableListOf<Int>()
        if (enableLte && lteBands.isNotEmpty()) {
            rans += AccessNetworkConstants.AccessNetworkType.EUTRAN
            counts += lteBands.size
            bands += lteBands
        }
        if (enableNr && nrBands.isNotEmpty()) {
            rans += AccessNetworkConstants.AccessNetworkType.NGRAN
            counts += nrBands.size
            bands += nrBands
        }
        if (enableWcdma && wcdmaBands.isNotEmpty()) {
            rans += AccessNetworkConstants.AccessNetworkType.UTRAN
            counts += wcdmaBands.size
            bands += wcdmaBands
        }
        val channelRan = when {
            arfcn == null -> 0
            servingRat?.contains("NR") == true || (enableNr && !enableLte) ->
                AccessNetworkConstants.AccessNetworkType.NGRAN
            else -> AccessNetworkConstants.AccessNetworkType.EUTRAN
        }
        if (rans.isNotEmpty() || arfcn != null) {
            val useRans = if (rans.isNotEmpty()) rans else listOf(channelRan)
            val useCounts = if (rans.isNotEmpty()) counts else listOf(0)
            val useBands = if (rans.isNotEmpty()) bands else emptyList()
            runCatching {
                svc.setSelection(
                    subId,
                    useRans.toIntArray(),
                    useCounts.toIntArray(),
                    useBands.toIntArray(),
                    if (arfcn != null) channelRan else 0,
                    arfcn?.let { intArrayOf(it) } ?: intArrayOf(),
                )
            }.onSuccess { messages += it }.onFailure { messages += "Band/频点: ${it.message}" }
        }
        if (pci != null && pci >= 0 && arfcn != null) {
            runCatching { svc.lockPci(subId, pci, arfcn, servingRat.orEmpty()) }
                .onSuccess { messages += it }
                .onFailure { messages += "PCI: ${it.message}" }
        } else if (pci != null) {
            messages += "PCI 锁定需要同时填写频点"
        }
        val result = messages.joinToString("\n")
        _state.update { it.copy(lastResult = result) }
        return result
    }

    fun clearLock(subId: Int): String {
        val svc = service ?: return "Shizuku UserService 未连接"
        val result = runCatching { svc.clearSelection(subId) }.getOrElse { "清除失败: ${it.message}" }
        _state.update { it.copy(lastResult = result) }
        return result
    }

    private fun bind() {
        runCatching { Shizuku.bindUserService(userServiceArgs, connection) }
            .onFailure { t -> _state.update { it.copy(lastResult = "绑定失败: ${t.message}") } }
    }

    private fun isInstalled(): Boolean {
        return runCatching {
            context.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            true
        }.getOrDefault(false)
    }

    companion object {
        private const val REQUEST_CODE = 1101
    }
}
