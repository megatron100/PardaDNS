package com.example.pardadns.domain.model

import com.example.pardadns.data.model.DnsProvider

/** One-shot command emitted by [com.example.pardadns.domain.usecase.ToggleVpnUseCase] and handled by MainActivity. */
sealed class VpnAction {
    data class Connect(val provider: DnsProvider) : VpnAction()
    data object Disconnect : VpnAction()
}
