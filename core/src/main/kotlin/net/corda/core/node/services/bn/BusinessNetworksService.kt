package net.corda.core.node.services.bn

import net.corda.core.DoNotImplement

@DoNotImplement
interface BusinessNetworksService {

    fun getAllBusinessNetworkIds(): List<String>

    fun getAllMemberships(): List<BusinessNetworkMembership>

    fun getAllGroups(): List<BusinessNetworkGroup>
}