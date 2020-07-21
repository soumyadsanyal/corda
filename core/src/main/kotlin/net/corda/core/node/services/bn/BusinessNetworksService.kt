package net.corda.core.node.services.bn

import net.corda.core.DoNotImplement
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.bn.MembershipManagementFlow
import net.corda.core.identity.Party

@DoNotImplement
@Suppress("TooManyFunctions")
interface BusinessNetworksService {

    fun getAllBusinessNetworkIds(): List<String>

    fun businessNetworkExists(networkId: String): Boolean

    fun getMembership(networkId: String, party: Party): BusinessNetworkMembership?

    fun getMembership(linearId: UniqueIdentifier): BusinessNetworkMembership?

    fun getAllMembershipsWithStatus(networkId: String, vararg statuses: MembershipStatus): List<BusinessNetworkMembership>

    fun getMembersAuthorisedToModifyMembership(networkId: String): List<BusinessNetworkMembership>

    fun businessNetworkGroupExists(groupId: UniqueIdentifier): Boolean

    fun getBusinessNetworkGroup(groupId: UniqueIdentifier): BusinessNetworkGroup?

    fun getAllBusinessNetworkGroups(networkId: String): List<BusinessNetworkGroup>

    fun activateMembershipFlow(membershipId: UniqueIdentifier, notary: Party?): MembershipManagementFlow<*>

    fun createBusinessNetworkFlow(
            networkId: UniqueIdentifier,
            businessIdentity: BNIdentity?,
            groupId: UniqueIdentifier,
            groupName: String?,
            notary: Party?
    ): MembershipManagementFlow<*>

    fun createGroupFlow(
            networkId: String,
            groupId: UniqueIdentifier,
            groupName: String?,
            additionalParticipants: Set<UniqueIdentifier>,
            notary: Party?
    ): MembershipManagementFlow<*>

    fun deleteGroupFlow(groupId: UniqueIdentifier, notary: Party?): MembershipManagementFlow<*>

    fun modifyBusinessIdentityFlow(membershipId: UniqueIdentifier, businessIdentity: BNIdentity, notary: Party?): MembershipManagementFlow<*>

    fun modifyGroupFlow(groupId: UniqueIdentifier, name: String?, participants: Set<UniqueIdentifier>?, notary: Party?): MembershipManagementFlow<*>

    fun modifyRolesFlow(membershipId: UniqueIdentifier, roles: Set<BNRole>, notary: Party?): MembershipManagementFlow<*>

    fun requestMembershipFlow(authorisedParty: Party, networkId: String, businessIdentity: BNIdentity?, notary: Party?): MembershipManagementFlow<*>

    fun revokeMembershipFlow(membershipId: UniqueIdentifier, notary: Party?): MembershipManagementFlow<*>

    fun suspendMembershipFlow(membershipId: UniqueIdentifier, notary: Party?): MembershipManagementFlow<*>
}