package net.corda.bn.states

import net.corda.bn.contracts.MembershipContract
import net.corda.bn.schemas.MembershipStateSchemaV1
import net.corda.core.contracts.BelongsToContract
import net.corda.core.contracts.LinearState
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.AbstractParty
import net.corda.core.node.services.bn.AdminPermission
import net.corda.core.node.services.bn.BNRole
import net.corda.core.node.services.bn.MembershipIdentity
import net.corda.core.node.services.bn.MembershipStatus
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.QueryableState
import java.time.Instant

/**
 * Represents a membership on the ledger.
 *
 * @property identity Identity of a member.
 * @property networkId Unique identifier of a Business Network membership belongs to.
 * @property status Status of the state (i.e. PENDING, ACTIVE, SUSPENDED).
 * @property roles Set of all the roles associated to the membership.
 * @property issued Timestamp when the state has been issued.
 * @property modified Timestamp when the state has been modified last time.
 */
@BelongsToContract(MembershipContract::class)
data class MembershipState(
        val identity: MembershipIdentity,
        val networkId: String,
        val status: MembershipStatus,
        val roles: Set<BNRole> = emptySet(),
        val issued: Instant = Instant.now(),
        val modified: Instant = issued,
        override val linearId: UniqueIdentifier = UniqueIdentifier(),
        override val participants: List<AbstractParty>
) : LinearState, QueryableState {

    override fun generateMappedObject(schema: MappedSchema) = when (schema) {
        is MembershipStateSchemaV1 -> MembershipStateSchemaV1.PersistentMembershipState(
                cordaIdentity = identity.cordaIdentity,
                networkId = networkId,
                status = status
        )
        else -> throw IllegalArgumentException("Unrecognised schema $schema")
    }

    override fun supportedSchemas() = listOf(MembershipStateSchemaV1)

    /** Indicates whether membership is in [MembershipStatus.PENDING] status. **/
    fun isPending() = status == MembershipStatus.PENDING

    /** Indicates whether membership is in [MembershipStatus.ACTIVE] status. **/
    fun isActive() = status == MembershipStatus.ACTIVE

    /** Indicates whether membership is in [MembershipStatus.SUSPENDED] status. **/
    fun isSuspended() = status == MembershipStatus.SUSPENDED

    /**
     * Iterates through all roles yielding set of all permissions given to them.
     *
     * @return Set of all roles given to all [MembershipState.roles].
     */
    private fun permissions() = roles.flatMap { it.permissions }.toSet()

    /** Indicates whether membership is authorised to activate memberships. **/
    fun canActivateMembership() = AdminPermission.CAN_ACTIVATE_MEMBERSHIP in permissions()

    /** Indicates whether membership is authorised to suspend memberships. **/
    fun canSuspendMembership() = AdminPermission.CAN_SUSPEND_MEMBERSHIP in permissions()

    /** Indicates whether membership is authorised to revoke memberships. **/
    fun canRevokeMembership() = AdminPermission.CAN_REVOKE_MEMBERSHIP in permissions()

    /** Indicates whether membership is authorised to modify memberships' roles. **/
    fun canModifyRoles() = AdminPermission.CAN_MODIFY_ROLE in permissions()

    /** Indicates whether membership is authorised to modify memberships' business identity. **/
    fun canModifyBusinessIdentity() = AdminPermission.CAN_MODIFY_BUSINESS_IDENTITY in permissions()

    /** Indicates whether membership is authorised to modify memberships' groups. **/
    fun canModifyGroups() = AdminPermission.CAN_MODIFY_GROUPS in permissions()

    /** Indicates whether membership has any administrative permission. **/
    fun canModifyMembership() = permissions().any { it is AdminPermission }
}