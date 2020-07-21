package net.corda.bn.flows

import net.corda.bn.schemas.GroupStateSchemaV1
import net.corda.bn.schemas.MembershipStateSchemaV1
import net.corda.bn.states.GroupState
import net.corda.bn.states.MembershipState
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.bn.MembershipManagementFlow
import net.corda.core.identity.Party
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.bn.BNIdentity
import net.corda.core.node.services.bn.BNRole
import net.corda.core.node.services.bn.BusinessNetworkGroup
import net.corda.core.node.services.bn.BusinessNetworkMembership
import net.corda.core.node.services.bn.BusinessNetworksService
import net.corda.core.node.services.bn.MembershipStatus
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.serialization.SingletonSerializeAsToken

class MembershipStorage(private val vaultService: VaultService) {

    fun businessNetworkExists(networkId: String): Boolean {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
                .and(networkIdCriteria(networkId))
        return vaultService.queryBy<MembershipState>(criteria).states.isNotEmpty()
    }

    fun getMembership(networkId: String, party: Party): StateAndRef<MembershipState>? {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(networkIdCriteria(networkId))
                .and(identityCriteria(party))
        val states = vaultService.queryBy<MembershipState>(criteria).states
        return states.maxBy { it.state.data.modified }
    }

    fun getMembership(linearId: UniqueIdentifier): StateAndRef<MembershipState>? {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(linearId)))
        val states = vaultService.queryBy<MembershipState>(criteria).states
        return states.maxBy { it.state.data.modified }
    }

    fun getAllMembershipsWithStatus(networkId: String, vararg statuses: MembershipStatus): List<StateAndRef<MembershipState>> {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(networkIdCriteria(networkId))
                .and(statusCriteria(statuses.toList()))
        return vaultService.queryBy<MembershipState>(criteria).states
    }

    fun getMembersAuthorisedToModifyMembership(networkId: String): List<StateAndRef<MembershipState>> = getAllMembershipsWithStatus(
            networkId,
            MembershipStatus.ACTIVE, MembershipStatus.SUSPENDED
    ).filter {
        it.state.data.toBusinessNetworkMembership().canModifyMembership()
    }

    /** Instantiates custom vault query criteria for finding membership with given [networkId]. **/
    private fun networkIdCriteria(networkId: String) = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::networkId.equal(networkId) })

    /** Instantiates custom vault query criteria for finding membership with given [cordaIdentity]. **/
    private fun identityCriteria(cordaIdentity: Party) = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::cordaIdentity.equal(cordaIdentity) })

    /** Instantiates custom vault query criteria for finding membership with any of given [statuses]. **/
    private fun statusCriteria(statuses: List<MembershipStatus>) = QueryCriteria.VaultCustomQueryCriteria(builder { MembershipStateSchemaV1.PersistentMembershipState::status.`in`(statuses) })
}

class GroupStorage(private val vaultService: VaultService) {

    fun businessNetworkGroupExists(groupId: UniqueIdentifier): Boolean {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
                .and(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(groupId)))
        return vaultService.queryBy<GroupState>(criteria).states.isNotEmpty()
    }

    fun getBusinessNetworkGroup(groupId: UniqueIdentifier): StateAndRef<GroupState>? {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(QueryCriteria.LinearStateQueryCriteria(linearId = listOf(groupId)))
        val states = vaultService.queryBy<GroupState>(criteria).states
        return states.maxBy { it.state.data.modified }
    }

    fun getAllBusinessNetworkGroups(networkId: String): List<StateAndRef<GroupState>> {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
                .and(networkIdCriteria(networkId))
        return vaultService.queryBy<GroupState>(criteria).states
    }

    /** Instantiates custom vault query criteria for finding Business Network Group with given [networkId]. **/
    private fun networkIdCriteria(networkId: String) = QueryCriteria.VaultCustomQueryCriteria(builder { GroupStateSchemaV1.PersistentGroupState::networkId.equal(networkId) })
}

/**
 * Service which handles all Business Network related vault queries.
 *
 * Each method querying vault for Business Network information must be included here.
 */
@Suppress("SpreadOperator", "TooManyFunctions")
class VaultBusinessNetworksService(private val vaultService: VaultService) : BusinessNetworksService, SingletonSerializeAsToken() {

    val membershipStorage = MembershipStorage(vaultService)
    val groupStorage = GroupStorage(vaultService)

    override fun getAllBusinessNetworkIds(): List<String> {
        val criteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.UNCONSUMED)
        return vaultService.queryBy<MembershipState>(criteria).states.map { it.state.data.networkId }.toSet().toList()
    }

    /**
     * Checks whether Business Network with [networkId] ID exists.
     *
     * @param networkId ID of the Business Network.
     */
    override fun businessNetworkExists(networkId: String): Boolean = membershipStorage.businessNetworkExists(networkId)

    /**
     * Queries for membership with [party] identity inside Business Network with [networkId] ID.
     *
     * @param networkId ID of the Business Network.
     * @param party Identity of the member.
     *
     * @return Membership state of member matching the query. If that member doesn't exist, returns [null].
     */
    override fun getMembership(networkId: String, party: Party): BusinessNetworkMembership? =
            membershipStorage.getMembership(networkId, party)?.state?.data?.toBusinessNetworkMembership()

    /**
     * Queries for membership with [linearId] linear ID.
     *
     * @param linearId Linear ID of the [MembershipState].
     *
     * @return Membership state matching the query. If that membership doesn't exist, returns [null].
     */
    override fun getMembership(linearId: UniqueIdentifier): BusinessNetworkMembership? =
            membershipStorage.getMembership(linearId)?.state?.data?.toBusinessNetworkMembership()

    /**
     * Queries for all the membership states inside Business Network with [networkId] with one of [statuses].
     *
     * @param networkId ID of the Business Network.
     * @param statuses [MembershipStatus] of the memberships to be fetched.
     *
     * @return List of state and ref pairs of memberships matching the query.
     */
    override fun getAllMembershipsWithStatus(networkId: String, vararg statuses: MembershipStatus): List<BusinessNetworkMembership> =
            membershipStorage.getAllMembershipsWithStatus(networkId, *statuses).map { it.state.data.toBusinessNetworkMembership() }

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
    override fun businessNetworkGroupExists(groupId: UniqueIdentifier): Boolean = groupStorage.businessNetworkGroupExists(groupId)

    /**
     * Queries for Business Network Group with [groupId] ID.
     *
     * @param groupId ID of the Business Network Group.
     *
     * @return Business Network Group matching the query. If that group doesn't exist, return [null].
     */
    override fun getBusinessNetworkGroup(groupId: UniqueIdentifier): BusinessNetworkGroup? =
            groupStorage.getBusinessNetworkGroup(groupId)?.state?.data?.toBusinessNetworkGroup()

    /**
     * Queries for all Business Network Groups inside Business Network with [networkId] ID.
     *
     * @param networkId ID of the Business Network.
     *
     * @return List of state and ref pairs of Business Network Groups.
     */
    override fun getAllBusinessNetworkGroups(networkId: String): List<BusinessNetworkGroup> =
            groupStorage.getAllBusinessNetworkGroups(networkId).map { it.state.data.toBusinessNetworkGroup() }

    override fun activateMembershipFlow(membershipId: UniqueIdentifier, notary: Party?): MembershipManagementFlow<*> = ActivateMembershipFlow(membershipId, notary)

    override fun createBusinessNetworkFlow(
            networkId: UniqueIdentifier,
            businessIdentity: BNIdentity?,
            groupId: UniqueIdentifier,
            groupName: String?,
            notary: Party?
    ): MembershipManagementFlow<*> = CreateBusinessNetworkFlow(networkId, businessIdentity, groupId, groupName, notary)

    override fun createGroupFlow(
            networkId: String,
            groupId: UniqueIdentifier,
            groupName: String?,
            additionalParticipants: Set<UniqueIdentifier>,
            notary: Party?
    ): MembershipManagementFlow<*> = CreateGroupFlow(networkId, groupId, groupName, additionalParticipants, notary)

    override fun deleteGroupFlow(groupId: UniqueIdentifier, notary: Party?): MembershipManagementFlow<*> = DeleteGroupFlow(groupId, notary)

    override fun modifyBusinessIdentityFlow(membershipId: UniqueIdentifier, businessIdentity: BNIdentity, notary: Party?): MembershipManagementFlow<*> =
            ModifyBusinessIdentityFlow(membershipId, businessIdentity, notary)

    override fun modifyGroupFlow(groupId: UniqueIdentifier, name: String?, participants: Set<UniqueIdentifier>?, notary: Party?): MembershipManagementFlow<*> =
            ModifyGroupFlow(groupId, name, participants, notary)

    override fun modifyRolesFlow(membershipId: UniqueIdentifier, roles: Set<BNRole>, notary: Party?): MembershipManagementFlow<*> =
            ModifyRolesFlow(membershipId, roles, notary)

    override fun requestMembershipFlow(authorisedParty: Party, networkId: String, businessIdentity: BNIdentity?, notary: Party?): MembershipManagementFlow<*> =
            RequestMembershipFlow(authorisedParty, networkId, businessIdentity, notary)

    override fun revokeMembershipFlow(membershipId: UniqueIdentifier, notary: Party?): MembershipManagementFlow<*> = RevokeMembershipFlow(membershipId, notary)

    override fun suspendMembershipFlow(membershipId: UniqueIdentifier, notary: Party?): MembershipManagementFlow<*> = SuspendMembershipFlow(membershipId, notary)
}
