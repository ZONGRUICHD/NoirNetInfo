package com.zongruichd.noirnetinfo.ui.home

import android.app.Application
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.zongruichd.noirnetinfo.data.NetworkCollector
import com.zongruichd.noirnetinfo.data.NetworkSnapshot
import com.zongruichd.noirnetinfo.data.PublicIpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeUiState(
    val snapshot: NetworkSnapshot? = null,
    val refreshing: Boolean = false,
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(HomeUiState(refreshing = true))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

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
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            val local = withContext(Dispatchers.IO) {
                NetworkCollector.collect(getApplication())
            }
            _state.update {
                it.copy(
                    snapshot = local.copy(publicIp = it.snapshot?.publicIp?.copy(loading = true) ?: local.publicIp.copy(loading = true)),
                    refreshing = false,
                )
            }
            val publicIp = withContext(Dispatchers.IO) { PublicIpClient.fetch() }
            _state.update { current ->
                current.copy(snapshot = current.snapshot?.copy(publicIp = publicIp))
            }
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
        super.onCleared()
    }
}
