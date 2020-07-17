package net.corda.bn.flows

import net.corda.bn.schemas.GroupStateSchemaV1
import net.corda.bn.schemas.MembershipStateSchemaV1
import net.corda.bn.states.GroupState
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.bn.BusinessNetworkGroup
import net.corda.core.node.services.bn.BusinessNetworkMembership
import net.corda.core.node.services.bn.BusinessNetworksService
import net.corda.core.node.services.bn.MembershipStatus
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.serialization.SingletonSerializeAsToken
import java.lang.IllegalStateException

/**
 * Service which handles all Business Network related vault queries.
 *
 * Each method querying vault for Business Network information must be included here.
 */
class VaultBusinessNetworksService(private val vaultService: VaultService) : BusinessNetworksService, SingletonSerializeAsToken() {

    override fun getAllBusinessNetworkIds(): List<UniqueIdentifier> {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        return vaultService.queryBy<MembershipState>(criteria).states.map { it.state.data.networkId }.toSet().toList()
    }

    /**
     * Checks whether Business Network with [networkId] ID exists.
     *
     * @param networkId ID of the Business Network.
     */
    override fun businessNetworkExists(networkId: String): Boolean {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
                .and(membershipNetworkIdCriteria(networkId))
        return vaultService.queryBy<MembershipState>(criteria).states.isNotEmpty()
    }

    /**
     * Queries for membership with [party] identity inside Business Network with [networkId] ID.
     *
     * @param networkId ID of the Business Network.
     * @param party Identity of the member.
     *
     * @return Membership state of member matching the query. If that member doesn't exist, returns [null].
     */
    override fun getMembership(networkId: String, party: Party): BusinessNetworkMembership? {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(membershipNetworkIdCriteria(networkId))
                .and(identityCriteria(party))
        val states = vaultService.queryBy<MembershipState>(criteria).states
        val membershipState = states.maxBy { it.state.data.modified }
        return membershipState?.state?.data?.toBusinessNetworkMembership()
    }

    /**
     * Queries for membership with [linearId] linear ID.
     *
     * @param linearId Linear ID of the [MembershipState].
     *
     * @return Membership state matching the query. If that membership doesn't exist, returns [null].
     */
    override fun getMembership(linearId: UniqueIdentifier): BusinessNetworkMembership? {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(linearIdCriteria(linearId))
        val states = vaultService.queryBy<MembershipState>(criteria).states
        val membershipState = states.maxBy { it.state.data.modified }
        return membershipState?.state?.data?.toBusinessNetworkMembership()
    }

    /**
     * Queries for all the membership states inside Business Network with [networkId] with one of [statuses].
     *
     * @param networkId ID of the Business Network.
     * @param statuses [MembershipStatus] of the memberships to be fetched.
     *
     * @return List of state and ref pairs of memberships matching the query.
     */
    override fun getAllMembershipsWithStatus(networkId: String, vararg statuses: MembershipStatus): List<BusinessNetworkMembership> {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(membershipNetworkIdCriteria(networkId))
                .and(statusCriteria(statuses.toList()))
        val membershipStates = vaultService.queryBy<MembershipState>(criteria).states
        return membershipStates.map { it.state.data.toBusinessNetworkMembership() }
    }

    /**
     * Queries for all members inside Business Network with [networkId] ID authorised to modify membership
     * (can activate, suspend or revoke membership).
     *
     * @param networkId ID of the Business Network.
     *
     * @return List of state and ref pairs of authorised members' membership states.
     */
    override fun getMembersAuthorisedToModifyMembership(networkId: String): List<BusinessNetworkMembership> = getAllMembershipsWithStatus(
            networkId,
            MembershipStatus.ACTIVE, MembershipStatus.SUSPENDED
    ).filter {
        it.canModifyMembership()
    }

    /**
     * Checks whether Business Network Group with [groupId] ID exists.
     *
     * @param groupId ID of the Business Network Group.
     */
    override fun businessNetworkGroupExists(groupId: UniqueIdentifier): Boolean {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
                .and(linearIdCriteria(groupId))
        return vaultService.queryBy<GroupState>(criteria).states.isNotEmpty()
    }

    /**
     * Queries for Business Network Group with [groupId] ID.
     *
     * @param groupId ID of the Business Network Group.
     *
     * @return Business Network Group matching the query. If that group doesn't exist, return [null].
     */
    override fun getBusinessNetworkGroup(groupId: UniqueIdentifier): BusinessNetworkGroup? {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(linearIdCriteria(groupId))
        val states = vaultService.queryBy<GroupState>(criteria).states
        val groupState = states.maxBy { it.state.data.modified }
        return groupState?.state?.data?.toBusinessNetworkGroup()
    }

    /**
     * Queries for all Business Network Groups inside Business Network with [networkId] ID.
     *
     * @param networkId ID of the Business Network.
     *
     * @return List of state and ref pairs of Business Network Groups.
     */
    override fun getAllBusinessNetworkGroups(networkId: String): List<BusinessNetworkGroup> {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(groupNetworkIdCriteria(networkId))
        val groupStates = vaultService.queryBy<GroupState>(criteria).states
        return groupStates.map { it.state.data.toBusinessNetworkGroup() }
    }

    fun toMembershipState(membership: BusinessNetworkMembership): StateAndRef<MembershipState> {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(linearIdCriteria(membership.membershipId))
        val states = vaultService.queryBy<MembershipState>(criteria).states
        return states.maxBy { it.state.data.modified }
                ?: throw IllegalStateException("Could not find membership state with ${membership.membershipId} linear ID")
    }

    fun toGroupState(group: BusinessNetworkGroup): StateAndRef<GroupState> {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(linearIdCriteria(group.groupId))
        val states = vaultService.queryBy<GroupState>(criteria).states
        return states.maxBy { it.state.data.modified }
                ?: throw IllegalStateException("Could not find group state with ${group.groupId} linear ID")
    }

    /** Instantiates custom vault query criteria for finding membership with given [networkId]. **/
    private fun membershipNetworkIdCriteria(networkId: String) = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::networkId.equal(networkId) })

    /** Instantiates custom vault query criteria for finding Business Network Group with given [networkId]. **/
    private fun groupNetworkIdCriteria(networkId: String) = QueryCriteria.VaultCustomQueryCriteria(builder { GroupStateSchemaV1.PersistentGroupState::networkId.equal(networkId) })

    /** Instantiates custom vault query criteria for finding membership with given [cordaIdentity]. **/
    private fun identityCriteria(cordaIdentity: Party) = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::cordaIdentity.equal(cordaIdentity) })

    /** Instantiates custom vault query criteria for finding membership with any of given [statuses]. **/
    private fun statusCriteria(statuses: List<MembershipStatus>) = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::status.`in`(statuses) })

    /** Instantiates custom vault query criteria for finding linear state with given [linearId]. **/
    private fun linearIdCriteria(linearId: UniqueIdentifier) = QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId))
}
