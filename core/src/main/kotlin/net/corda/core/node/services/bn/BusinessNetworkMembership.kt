package net.corda.core.node.services.bn

import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.time.Instant

@CordaSerializable
data class BusinessNetworkMembership(
        val identity: MembershipIdentity,
        val networkId: String,
        val status: MembershipStatus,
        val roles: Set<BNRole> = emptySet(),
        val issued: Instant = Instant.now(),
        val modified: Instant = issued,
        val membershipId: String
)

/**
 * Represents identity addition associated with Business Network membership. Every custom Business Network related additional identity
 * should implement this interface.
 */
@CordaSerializable
interface BNIdentity

/**
 * Represents identity associated with Business Network membership made up of 2 components: required Corda identity and optional custom
 * defined business identity.
 *
 * @property cordaIdentity Required Corda X509 identity associated with membership.
 * @property businessIdentity Optional custom defined identity associated with same membership.
 */
@CordaSerializable
data class MembershipIdentity(val cordaIdentity: Party, val businessIdentity: BNIdentity? = null)

/**
 * Statuses that membership can go through.
 */
@CordaSerializable
enum class MembershipStatus {
    /**
     * Newly submitted state which hasn't been approved by authorised member yet. Pending members can't transact on the Business Network.
     */
    PENDING,

    /**
     * Active members can transact on the Business Network and modify other memberships if they are authorised.
     */
    ACTIVE,

    /**
     * Suspended members can't transact on the Business Network or modify other memberships. Suspended members can be activated back.
     */
    SUSPENDED
}

/**
 * Represents role associated with Business Network membership. Every custom Business Network related role should extend this class.
 *
 * @property name Name of the role.
 * @property permissions Set of permissions given to the role.
 */
@CordaSerializable
open class BNRole(val name: String, val permissions: Set<BNPermission>) {
    override fun equals(other: Any?) = other is BNRole && name == other.name && permissions == other.permissions
    override fun hashCode() = name.hashCode() + 31 * permissions.hashCode()
}

/**
 * Represents Business Network Operator (BNO) role which has all Business Network administrative permissions given.
 */
@CordaSerializable
class BNORole : BNRole("BNO", AdminPermission.values().toSet())

/**
 * Represents simple member which doesn't have any Business Network administrative permission given.
 */
@CordaSerializable
class MemberRole : BNRole("Member", emptySet())

/**
 * Represents permission given in the context of Business Network membership. Every custom Business Network related permission should
 * implement this interface.
 */
@CordaSerializable
interface BNPermission

/**
 * Business Network administrative permissions that can be given to a role.
 */
@CordaSerializable
enum class AdminPermission : BNPermission {
    /**
     * Enables member to activate Business Network memberships.
     */
    CAN_ACTIVATE_MEMBERSHIP,

    /**
     * Enables member to suspend Business Network memberships.
     */
    CAN_SUSPEND_MEMBERSHIP,

    /**
     * Enables member to revoke Business Network memberships.
     */
    CAN_REVOKE_MEMBERSHIP,

    /**
     * Enables member to modify memberships' roles.
     */
    CAN_MODIFY_ROLE,

    /**
     * Enables member to modify memberships' business identity.
     */
    CAN_MODIFY_BUSINESS_IDENTITY,

    /**
     * Enables member to modify Business Networks' associated [GroupState]s.
     */
    CAN_MODIFY_GROUPS
}