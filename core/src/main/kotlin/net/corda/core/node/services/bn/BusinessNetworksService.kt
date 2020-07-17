package net.corda.core.node.services.bn

import net.corda.core.DoNotImplement
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party

@DoNotImplement
interface BusinessNetworksService {

    fun getAllBusinessNetworkIds(): List<UniqueIdentifier>

    fun businessNetworkExists(networkId: String): Boolean

    fun getMembership(networkId: String, party: Party): BusinessNetworkMembership?

    fun getMembership(linearId: UniqueIdentifier): BusinessNetworkMembership?

    fun getAllMembershipsWithStatus(networkId: String, vararg statuses: MembershipStatus): List<BusinessNetworkMembership>

    fun getMembersAuthorisedToModifyMembership(networkId: String): List<BusinessNetworkMembership>

    fun businessNetworkGroupExists(groupId: UniqueIdentifier): Boolean

    fun getBusinessNetworkGroup(groupId: UniqueIdentifier): BusinessNetworkGroup?

    fun getAllBusinessNetworkGroups(networkId: String): List<BusinessNetworkGroup>
}