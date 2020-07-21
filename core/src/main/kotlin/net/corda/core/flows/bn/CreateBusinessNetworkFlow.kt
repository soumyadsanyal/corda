package net.corda.core.flows.bn

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.bn.BNIdentity
import net.corda.core.node.services.bn.BusinessNetworksService

@StartableByRPC
class CreateBusinessNetworkFlow(
        private val networkId: UniqueIdentifier = UniqueIdentifier(),
        private val businessIdentity: BNIdentity? = null,
        private val groupId: UniqueIdentifier = UniqueIdentifier(),
        private val groupName: String? = null,
        private val notary: Party? = null
) : MembershipManagementCoreFlow<Unit>() {

    @Suspendable
    override fun getConcreteImplementationFlow(service: BusinessNetworksService): MembershipManagementFlow<Unit> = service.createBusinessNetworkFlow(networkId, businessIdentity, groupId, groupName, notary)
}