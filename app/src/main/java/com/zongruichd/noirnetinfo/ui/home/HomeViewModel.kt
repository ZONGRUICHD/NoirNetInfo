package com.zongruichd.noirnetinfo.ui.home

import android.app.Application
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zongruichd.noirnetinfo.data.CellularCollector
import com.zongruichd.noirnetinfo.data.NetworkCollector
import com.zongruichd.noirnetinfo.data.NetworkSnapshot
import com.zongruichd.noirnetinfo.data.PublicIpClient
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

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(HomeUiState(refreshing = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val shizukuController = ShizukuController(application)
    val shizuku: StateFlow<ShizukuUiState> = shizukuController.state

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
