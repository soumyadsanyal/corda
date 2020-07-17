package net.corda.core.node.services.bn

import net.corda.core.DoNotImplement

@DoNotImplement
interface BusinessNetworksServiceInternal {

    fun activateMembership(networkId: String, membership: BusinessNetworkMembership)
}