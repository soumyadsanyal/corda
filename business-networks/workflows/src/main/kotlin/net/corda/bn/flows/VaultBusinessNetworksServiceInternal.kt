package net.corda.bn.flows

import net.corda.bn.contracts.MembershipContract
import net.corda.core.node.services.bn.BusinessNetworkMembership
import net.corda.core.node.services.bn.BusinessNetworksService
import net.corda.core.node.services.bn.BusinessNetworksServiceInternal
import net.corda.core.node.services.bn.MembershipStatus
import net.corda.core.transactions.TransactionBuilder

class VaultBusinessNetworksServiceInternal(val service: VaultBusinessNetworksService) : BusinessNetworksServiceInternal {

    override fun activateMembership(networkId: String, membership: BusinessNetworkMembership) {
        // fetch signers
        val authorisedMemberships = service.getMembersAuthorisedToModifyMembership(networkId).toSet()
        val signers = authorisedMemberships.filter { it.isActive() }.map { it.identity.cordaIdentity }

        // building transaction
        val inputMembership = service.toMembershipState(membership)
        val outputMembership = inputMembership.state.data.copy(status = MembershipStatus.ACTIVE, modified = serviceHub.clock.instant())
        val requiredSigners = signers.map { it.owningKey }
        val builder = TransactionBuilder(notary ?: serviceHub.networkMapCache.notaryIdentities.first())
                .addInputState(membership)
                .addOutputState(outputMembership)
                .addCommand(MembershipContract.Commands.Activate(requiredSigners), requiredSigners)
        builder.verify(serviceHub)

        // collect signatures and finalise transaction
        val observerSessions = (outputMembership.participants - ourIdentity).map { initiateFlow(it) }
        return collectSignaturesAndFinaliseTransaction(builder, observerSessions, signers)
    }
}