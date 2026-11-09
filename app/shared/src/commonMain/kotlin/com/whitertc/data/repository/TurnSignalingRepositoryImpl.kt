package com.whitertc.data.repository

import com.whitertc.core.provider.RelayProviderRegistry
import com.whitertc.core.turn.DefaultSignalingResolver
import com.whitertc.core.turn.ResolvedTurnSession
import com.whitertc.domain.model.TurnProviderType
import com.whitertc.domain.repository.TurnSignalingRepository
import org.koin.core.annotation.Single

@Single
class TurnSignalingRepositoryImpl : TurnSignalingRepository {

    override suspend fun resolve(
        providerType: TurnProviderType,
        source: String,
        onProgress: ((step: Int, name: String, details: String) -> Unit)?
    ): ResolvedTurnSession {
        val provider = RelayProviderRegistry.get(providerType.id)
            ?: RelayProviderRegistry.findProvider(source)

        if (provider != null) {
            val sessions = provider.resolve(
                source = source,
                count = 1,
                onProgress = { current, total, stage ->
                    onProgress?.invoke(current + 1, stage, "Session ${current + 1}/$total")
                }
            )
            if (sessions.isNotEmpty()) return sessions.first()
        }

        return ResolvedTurnSession(
            callId = "direct",
            username = "",
            password = "",
            turnUrls = emptyList(),
            stunUrls = emptyList(),
            primaryTurnAddress = null
        )
    }

    override suspend fun resolveMultiple(
        providerType: TurnProviderType,
        source: String,
        count: Int,
        onProgress: ((resolvedCount: Int, total: Int, stage: String) -> Unit)?
    ): List<ResolvedTurnSession> {
        val provider = RelayProviderRegistry.get(providerType.id)
            ?: RelayProviderRegistry.findProvider(source)

        if (provider != null) {
            return provider.resolve(
                source = source,
                count = count,
                onProgress = { current, total, stage ->
                    onProgress?.invoke(current, total, stage)
                }
            )
        }

        return DefaultSignalingResolver.resolve(
            providerType = providerType.id,
            source = source,
            count = count,
            onProgress = { current, total, stage ->
                onProgress?.invoke(current, total, stage)
            }
        )
    }
}


