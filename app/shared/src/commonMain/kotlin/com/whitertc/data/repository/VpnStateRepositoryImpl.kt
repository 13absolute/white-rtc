package com.whitertc.data.repository

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import com.whitertc.domain.model.TunnelStats
import com.whitertc.domain.model.VpnStatus
import com.whitertc.domain.repository.VpnCommand
import com.whitertc.domain.repository.VpnStateRepository
import org.koin.core.annotation.Single

@Single
class VpnStateRepositoryImpl : VpnStateRepository {

    private val _status = MutableStateFlow<VpnStatus>(VpnStatus.Disconnected)
    private val _stats = MutableStateFlow(TunnelStats())
    private val _commands = MutableSharedFlow<VpnCommand>(
        extraBufferCapacity = 16,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun observeStatus(): Flow<VpnStatus> = _status.asStateFlow()

    override suspend fun updateStatus(status: VpnStatus) {
        _status.value = status
    }

    override fun observeStats(): Flow<TunnelStats> = _stats.asStateFlow()

    override suspend fun updateStats(stats: TunnelStats) {
        _stats.value = stats
    }

    override suspend fun sendCommand(command: VpnCommand) {
        _commands.emit(command)
    }

    override fun observeCommands(): Flow<VpnCommand> = _commands.asSharedFlow()
}
