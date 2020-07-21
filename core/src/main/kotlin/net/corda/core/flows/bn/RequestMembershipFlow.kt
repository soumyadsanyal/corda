package net.corda.core.flows.bn

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.StartableByRPC
import net.corda.core.identity.Party
import net.corda.core.node.services.bn.BNIdentity
import net.corda.core.node.services.bn.BusinessNetworksService

@StartableByRPC
class RequestMembershipFlow(
        private val authorisedParty: Party,
        private val networkId: String,
        private val businessIdentity: BNIdentity? = null,
        private val notary: Party? = null
) : MembershipManagementCoreFlow<Unit>() {

    @Suspendable
    override fun getConcreteImplementationFlow(service: BusinessNetworksService): MembershipManagementFlow<Unit> = service.requestMembershipFlow(authorisedParty, networkId, businessIdentity, notary)
}