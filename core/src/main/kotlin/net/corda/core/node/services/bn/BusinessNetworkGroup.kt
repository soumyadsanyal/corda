package net.corda.core.node.services.bn

import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.Party
import net.corda.core.serialization.CordaSerializable
import java.time.Instant

@CordaSerializable
data class BusinessNetworkGroup(
        val networkId: String,
        val name: String? = null,
        val issued: Instant = Instant.now(),
        val modified: Instant = issued,
        val groupId: UniqueIdentifier,
        val participants: List<Party>
)