package net.corda.node

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.internal.concurrent.transpose
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.nodeapi.internal.config.User
import net.corda.testing.*
import net.corda.testing.driver.driver
import net.corda.testing.internal.IntegrationTest
import net.corda.testing.internal.IntegrationTestSchemas
import net.corda.testing.internal.toDatabaseSchemaName
import org.assertj.core.api.Assertions.assertThat
import org.junit.ClassRule
import org.junit.Test

class CordappScanningDriverTest : IntegrationTest() {
    companion object {
        @ClassRule @JvmField
        val databaseSchemas = IntegrationTestSchemas(ALICE_NAME.toDatabaseSchemaName(), BOB_NAME.toDatabaseSchemaName(),
                DUMMY_NOTARY_NAME.toDatabaseSchemaName())
    }

    @Test
    fun `sub-classed initiated flow pointing to the same initiating flow as its super-class`() {
        val user = User("u", "p", setOf(startFlow<ReceiveFlow>()))
        // The driver will automatically pick up the annotated flows below
        driver {
            val (alice, bob) = listOf(
                    startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)),
                    startNode(providedName = BOB_NAME)).transpose().getOrThrow()
            val initiatedFlowClass = alice.rpcClientToNode()
                    .start(user.username, user.password)
                    .proxy
                    .startFlow(::ReceiveFlow, bob.nodeInfo.chooseIdentity())
                    .returnValue
            assertThat(initiatedFlowClass.getOrThrow()).isEqualTo(SendSubClassFlow::class.java.name)
        }
    }

    @StartableByRPC
    @InitiatingFlow
    class ReceiveFlow(val otherParty: Party) : FlowLogic<String>() {
        @Suspendable
        override fun call(): String = initiateFlow(otherParty).receive<String>().unwrap { it }
    }

    @InitiatedBy(ReceiveFlow::class)
    open class SendClassFlow(val otherPartySession: FlowSession) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = otherPartySession.send(javaClass.name)
    }

    @InitiatedBy(ReceiveFlow::class)
    class SendSubClassFlow(otherPartySession: FlowSession) : SendClassFlow(otherPartySession)
}
