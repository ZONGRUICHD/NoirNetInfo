package com.zongruichd.noirnetinfo.ui.home

import android.app.Application
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zongruichd.noirnetinfo.BuildConfig
import com.zongruichd.noirnetinfo.data.CellularCollector
import com.zongruichd.noirnetinfo.data.NetworkCollector
import com.zongruichd.noirnetinfo.data.NetworkSnapshot
import com.zongruichd.noirnetinfo.data.PublicIpClient
import com.zongruichd.noirnetinfo.data.UpdateClient
import com.zongruichd.noirnetinfo.shizuku.ShizukuController
import com.zongruichd.noirnetinfo.shizuku.ShizukuUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val snapshot: NetworkSnapshot? = null,
    val refreshing: Boolean = false,
)

data class UpdateUiState(
    val checking: Boolean = false,
    val downloading: Boolean = false,
    val progress: Float? = null,
    val latestTag: String? = null,
    val latestName: String? = null,
    val changelog: String? = null,
    val htmlUrl: String? = null,
    val apkUrl: String? = null,
    val hasUpdate: Boolean = false,
    val message: String? = null,
    val installUri: Uri? = null,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(HomeUiState(refreshing = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val shizukuController = ShizukuController(application)
    val shizuku: StateFlow<ShizukuUiState> = shizukuController.state

    private val _update = MutableStateFlow(UpdateUiState())
    val update: StateFlow<UpdateUiState> = _update.asStateFlow()

    private var refreshJob: Job? = null
    private var debounceJob: Job? = null
    private val connectivityManager = application.getSystemService(ConnectivityManager::class.java)

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = scheduleRefresh()
        override fun onLost(network: Network) = scheduleRefresh()
        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) =
            scheduleRefresh()
        override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) =
            scheduleRefresh()
    }

    init {
        runCatching { connectivityManager.registerDefaultNetworkCallback(networkCallback) }
        refresh()
        viewModelScope.launch {
            while (isActive) {
                delay(1500)
                refreshCellular()
            }
        }
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            shizukuController.refresh()
            val local = withContext(Dispatchers.IO) {
                NetworkCollector.collect(getApplication())
            }
            _state.update {
                it.copy(
                    snapshot = local.copy(
                        publicIp = it.snapshot?.publicIp?.copy(loading = true)
                            ?: local.publicIp.copy(loading = true),
                    ),
                    refreshing = false,
                )
            }
            val publicIp = withContext(Dispatchers.IO) { PublicIpClient.fetch() }
            _state.update { current ->
                current.copy(snapshot = current.snapshot?.copy(publicIp = publicIp))
            }
        }
    }

    fun requestShizuku() {
        shizukuController.requestPermission()
    }

    fun applyCellularLock(
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
    ) {
        viewModelScope.launch(Dispatchers.Default) {
            shizukuController.applyLock(
                subId, enableGsm, enableWcdma, enableLte, enableNr,
                lteBands, nrBands, wcdmaBands, arfcn, pci, servingRat,
            )
        }
    }

    fun clearCellularLock(subId: Int) {
        viewModelScope.launch(Dispatchers.Default) {
            shizukuController.clearLock(subId)
        }
    }

    fun checkUpdate() {
        viewModelScope.launch {
            _update.update { it.copy(checking = true, message = null) }
            runCatching {
                withContext(Dispatchers.IO) { UpdateClient.check() }
            }.onSuccess { release ->
                _update.update {
                    it.copy(
                        checking = false,
                        latestTag = release.tag,
                        latestName = release.name.ifBlank { release.tag },
                        changelog = release.changelog,
                        htmlUrl = release.htmlUrl,
                        apkUrl = release.apkUrl,
                        hasUpdate = release.newerThanCurrent && release.apkUrl != null,
                        message = when {
                            release.apkUrl == null -> "找到 Release，但没有 APK 附件"
                            release.newerThanCurrent -> "发现 ${release.tag}（当前 ${BuildConfig.VERSION_NAME}）"
                            else -> "已是最新版本 ${BuildConfig.VERSION_NAME}"
                        },
                    )
                }
            }.onFailure { err ->
                _update.update {
                    it.copy(checking = false, message = "检查失败：${err.message}")
                }
            }
        }
    }

    fun downloadUpdate() {
        val url = _update.value.apkUrl ?: return
        viewModelScope.launch {
            _update.update { it.copy(downloading = true, progress = 0f, message = "正在下载…") }
            runCatching {
                withContext(Dispatchers.IO) {
                    UpdateClient.download(getApplication(), url) { p ->
                        _update.update { it.copy(progress = p) }
                    }
                }
            }.onSuccess { uri ->
                _update.update {
                    it.copy(
                        downloading = false,
                        progress = 1f,
                        installUri = uri,
                        message = "下载完成，正在唤起安装",
                    )
                }
            }.onFailure { err ->
                _update.update {
                    it.copy(downloading = false, message = "下载失败：${err.message}")
                }
            }
        }
    }

    fun consumeInstallUri() {
        _update.update { it.copy(installUri = null) }
    }

    private suspend fun refreshCellular() {
        val current = _state.value.snapshot ?: return
        val app = getApplication<Application>()
        val (cellular, slots) = withContext(Dispatchers.IO) {
            CellularCollector.collect(app, current.phoneGranted, current.locationGranted)
        }
        _state.update { state ->
            val snap = state.snapshot ?: return@update state
            state.copy(
                snapshot = snap.copy(
                    cellular = cellular,
                    slots = slots,
                    collectedAtMillis = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun scheduleRefresh() {
        debounceJob?.cancel()
        debounceJob = viewModelScope.launch {
            delay(400)
            refresh()
        }
    }

    override fun onCleared() {
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        shizukuController.dispose()
        super.onCleared()
    }
}
