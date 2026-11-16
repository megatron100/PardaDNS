package com.example.pardadns.domain.usecase

import com.example.pardadns.data.model.DnsProvider
import com.example.pardadns.domain.model.VpnAction
import com.example.pardadns.domain.model.VpnState
import javax.inject.Inject

class ToggleVpnUseCase @Inject constructor() {

    operator fun invoke(currentState: VpnState, provider: DnsProvider): VpnAction =
        when {
            currentState is VpnState.Connected && currentState.providerName == provider.name ->
                VpnAction.Disconnect
            currentState is VpnState.Connecting ->
                // Treat any tap while connecting as a cancel request.
                VpnAction.Disconnect
            else -> VpnAction.Connect(provider)
        }
}
