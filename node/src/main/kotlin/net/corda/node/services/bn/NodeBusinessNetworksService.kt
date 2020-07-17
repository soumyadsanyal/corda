package net.corda.node.services.bn

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.internal.uncheckedCast
import net.corda.core.node.services.bn.BusinessNetworkGroup
import net.corda.core.node.services.bn.BusinessNetworkMembership
import net.corda.core.node.services.bn.BusinessNetworksService
import net.corda.core.node.services.bn.MembershipStatus
import net.corda.core.serialization.SingletonSerializeAsToken

class NodeBusinessNetworksService : BusinessNetworksService, SingletonSerializeAsToken() {

    private lateinit var service: BusinessNetworksService

    fun start(classLoader: ClassLoader, serviceClassName: String) {
        val serviceClass = classLoader.loadClass(serviceClassName)
        service = uncheckedCast(serviceClass.getConstructor().newInstance())
    }

    override fun getAllBusinessNetworkIds(): List<UniqueIdentifier> = service.getAllBusinessNetworkIds()

    override fun businessNetworkExists(networkId: String): Boolean = service.businessNetworkExists(networkId)

    override fun getMembership(networkId: String, party: Party): BusinessNetworkMembership? = service.getMembership(networkId, party)

    override fun getMembership(linearId: UniqueIdentifier): BusinessNetworkMembership? = service.getMembership(linearId)

    override fun getAllMembershipsWithStatus(networkId: String, vararg statuses: MembershipStatus): List<BusinessNetworkMembership> = service.getAllMembershipsWithStatus(networkId, *statuses)

    override fun getMembersAuthorisedToModifyMembership(networkId: String): List<BusinessNetworkMembership> = service.getMembersAuthorisedToModifyMembership(networkId)

    override fun businessNetworkGroupExists(groupId: UniqueIdentifier): Boolean = service.businessNetworkGroupExists(groupId)

    override fun getBusinessNetworkGroup(groupId: UniqueIdentifier): BusinessNetworkGroup? = service.getBusinessNetworkGroup(groupId)

    override fun getAllBusinessNetworkGroups(networkId: String): List<BusinessNetworkGroup> = service.getAllBusinessNetworkGroups(networkId)
}