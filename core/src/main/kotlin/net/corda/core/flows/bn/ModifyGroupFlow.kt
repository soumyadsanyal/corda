package net.corda.core.flows.bn

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.bn.BusinessNetworksService

@StartableByRPC
class ModifyGroupFlow(
        private val groupId: UniqueIdentifier,
        private val name: String? = null,
        private val participants: Set<UniqueIdentifier>? = null,
        private val notary: Party? = null
) : MembershipManagementCoreFlow<Unit>() {

    @Suspendable
    override fun getConcreteImplementationFlow(service: BusinessNetworksService): MembershipManagementFlow<Unit> = service.modifyGroupFlow(groupId, name, participants, notary)
}