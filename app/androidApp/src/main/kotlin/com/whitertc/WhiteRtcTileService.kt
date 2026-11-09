package com.whitertc

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.whitertc.domain.model.VpnStatus
import com.whitertc.domain.repository.VpnConfigRepository
import com.whitertc.domain.repository.VpnStateRepository
import com.whitertc.vpn.WhiteVpnService
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class WhiteRtcTileService : TileService(), KoinComponent {

    private val vpnStateRepository: VpnStateRepository by inject()
    private val vpnConfigRepository: VpnConfigRepository by inject()

    private var serviceScope: CoroutineScope? = null
    private var currentStatus: VpnStatus = VpnStatus.Disconnected
    private var activeProfileName: String? = null

    override fun onStartListening() {
        super.onStartListening()
        serviceScope?.cancel()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
        serviceScope = scope

        scope.launch {
            combine(
                vpnStateRepository.observeStatus(),
                vpnConfigRepository.observeProfiles()
            ) { status, profiles ->
                val active = profiles.firstOrNull { it.isActive }
                Pair(status, active?.name)
            }.collect { (status, profileName) ->
                currentStatus = status
                activeProfileName = profileName
                updateTileState(status, profileName)
            }
        }
    }

    override fun onStopListening() {
        serviceScope?.cancel()
        serviceScope = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (isLocked) {
            unlockAndRun { handleToggle() }
        } else {
            handleToggle()
        }
    }

    private fun handleToggle() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent != null) {
            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pendingIntent = PendingIntent.getActivity(
                    this,
                    0,
                    intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pendingIntent)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        when (currentStatus) {
            is VpnStatus.Connected, is VpnStatus.Connecting -> {
                WhiteVpnService.stop(this)
            }
            is VpnStatus.Disconnected, is VpnStatus.Error -> {
                WhiteVpnService.start(this)
            }
        }
    }

    private fun updateTileState(status: VpnStatus, profileName: String?) {
        val tile = qsTile ?: return
        val displayName = profileName?.ifBlank { null } ?: getString(R.string.app_name)

        tile.label = displayName

        when (status) {
            is VpnStatus.Connected -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.tile_connected)
                }
            }
            is VpnStatus.Connecting -> {
                tile.state = Tile.STATE_ACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.tile_connecting)
                }
            }
            is VpnStatus.Disconnected -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.tile_disconnected)
                }
            }
            is VpnStatus.Error -> {
                tile.state = Tile.STATE_INACTIVE
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    tile.subtitle = getString(R.string.tile_error)
                }
            }
        }

        tile.updateTile()
    }
}
