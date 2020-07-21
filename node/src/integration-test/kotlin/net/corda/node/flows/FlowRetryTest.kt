package net.corda.node.flows

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.CordaRuntimeException
import net.corda.core.flows.FlowExternalAsyncOperation
import net.corda.core.flows.FlowLogic
import net.corda.core.flows.FlowSession
import net.corda.core.flows.InitiatedBy
import net.corda.core.flows.InitiatingFlow
import net.corda.core.flows.StartableByRPC
import net.corda.core.flows.StateMachineRunId
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.identity.Party
import net.corda.core.internal.IdempotentFlow
import net.corda.core.messaging.startFlow
import net.corda.core.serialization.CordaSerializable
import net.corda.core.utilities.ProgressTracker
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.core.utilities.unwrap
import net.corda.node.services.Permissions
import net.corda.node.services.config.NodeConfiguration
import net.corda.node.services.statemachine.Checkpoint
import net.corda.node.services.statemachine.FlowStateMachineImpl
import net.corda.node.services.statemachine.FlowTimeoutException
import net.corda.node.services.statemachine.ReloadFlowFromCheckpointException
import net.corda.node.services.statemachine.StaffedFlowHospital
import net.corda.node.services.statemachine.StateTransitionException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.User
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.hibernate.exception.ConstraintViolationException
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.lang.management.ManagementFactory
import java.sql.SQLException
import java.sql.SQLTransientConnectionException
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.Collections
import java.util.HashSet
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeoutException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull

class FlowRetryTest {

    private companion object {
        val user = User("mark", "dadada", setOf(Permissions.all()))
    }

    @Before
    fun resetCounters() {
        InitiatorFlow.seen.clear()
        InitiatedFlow.seen.clear()
        TransientConnectionFailureFlow.retryCount = -1
        WrappedTransientConnectionFailureFlow.retryCount = -1
        GeneralExternalFailureFlow.retryCount = -1
        StaffedFlowHospital.DatabaseEndocrinologist.customConditions.add { true }
    }

    @After
    fun cleanUp() {
        StaffedFlowHospital.DatabaseEndocrinologist.customConditions.clear()
    }

    @Test(timeout = 300_000)
    fun `flows continue despite errors`() {
        val numSessions = 2
        val numIterations = 10
        val result: Any? = driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()

            val result = nodeAHandle.rpc.startFlow(
                ::InitiatorFlow,
                numSessions,
                numIterations,
                nodeBHandle.nodeInfo.singleIdentity()
            ).returnValue.getOrThrow()
            result
        }
        assertNotNull(result)
        assertEquals("$numSessions:$numIterations", result)
    }

    @Test(timeout = 300_000)
    fun `async operation deduplication id is stable accross retries`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            nodeAHandle.rpc.startFlow(::AsyncRetryFlow).returnValue.getOrThrow()
        }
    }

    @Test(timeout = 300_000)
    fun `flow gives up after number of exceptions, even if this is the first line of the flow`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            assertFailsWith<CordaRuntimeException> {
                nodeAHandle.rpc.startFlow(::RetryFlow).returnValue.getOrThrow()
            }
        }
    }

    @Test(timeout = 300_000)
    fun `flow that throws in constructor throw for the RPC client that attempted to start them`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {
            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            assertFailsWith<CordaRuntimeException> {
                nodeAHandle.rpc.startFlow(::ThrowingFlow).returnValue.getOrThrow()
            }
        }
    }

    @Test(timeout = 300_000)
    fun `SQLTransientConnectionExceptions thrown by hikari are retried 3 times and then kept in the checkpoints table`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()
            assertFailsWith<TimeoutException> {
                nodeAHandle.rpc.startFlow(::TransientConnectionFailureFlow, nodeBHandle.nodeInfo.singleIdentity())
                    .returnValue.getOrThrow(Duration.of(10, ChronoUnit.SECONDS))
            }
            assertEquals(3, TransientConnectionFailureFlow.retryCount)
            assertEquals(
                1,
                nodeAHandle.rpc.startFlow(::GetCheckpointNumberOfStatusFlow, Checkpoint.FlowStatus.HOSPITALIZED).returnValue.get()
            )
        }
    }

    @Test(timeout = 300_000)
    fun `Specific exception still detected even if it is nested inside another exception`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()
            assertFailsWith<TimeoutException> {
                nodeAHandle.rpc.startFlow(::WrappedTransientConnectionFailureFlow, nodeBHandle.nodeInfo.singleIdentity())
                    .returnValue.getOrThrow(Duration.of(10, ChronoUnit.SECONDS))
            }
            assertEquals(3, WrappedTransientConnectionFailureFlow.retryCount)
            assertEquals(
                1,
                nodeAHandle.rpc.startFlow(::GetCheckpointNumberOfStatusFlow, Checkpoint.FlowStatus.HOSPITALIZED).returnValue.get()
            )
        }
    }

    @Test(timeout = 300_000)
    fun `General external exceptions are not retried and propagate`() {
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val nodeBHandle = startNode(providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()

            assertFailsWith<CordaRuntimeException> {
                nodeAHandle.rpc.startFlow(
                    ::GeneralExternalFailureFlow,
                    nodeBHandle.nodeInfo.singleIdentity()
                ).returnValue.getOrThrow()
            }
            assertEquals(0, GeneralExternalFailureFlow.retryCount)
            assertEquals(
                1,
                nodeAHandle.rpc.startFlow(::GetCheckpointNumberOfStatusFlow, Checkpoint.FlowStatus.FAILED).returnValue.get()
            )
        }
    }

    @Test(timeout = 300_000)
    fun `Permission exceptions are not retried and propagate`() {
        val user = User("mark", "dadada", setOf())
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {

            val nodeAHandle = startNode(providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()

            assertThatExceptionOfType(CordaRuntimeException::class.java).isThrownBy {
                nodeAHandle.rpc.startFlow(::AsyncRetryFlow).returnValue.getOrThrow()
            }.withMessageStartingWith("User not authorized to perform RPC call")
            // This stays at -1 since the flow never even got called
            assertEquals(-1, GeneralExternalFailureFlow.retryCount)
        }
    }

    @Test(timeout = 300_000)
    fun `flow will reload from its checkpoint after suspending when reloadCheckpointAfterSuspend is true`() {
        val reloadCounts = mutableMapOf<StateMachineRunId, Int>()
        FlowStateMachineImpl.onReloadFlowFromCheckpoint = { id ->
            reloadCounts.compute(id) { _, value -> value?.plus(1) ?: 1 }
        }
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {

            val nodeAHandle = startNode(
                providedName = ALICE_NAME,
                rpcUsers = listOf(user),
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()
            val nodeBHandle = startNode(
                providedName = BOB_NAME,
                rpcUsers = listOf(user),
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()

            val handle = nodeAHandle.rpc.startFlow(::ReloadFromCheckpointFlow, nodeBHandle.nodeInfo.singleIdentity(), false, false)
            val flowStartedByAlice = handle.id
            handle.returnValue.getOrThrow()
            assertEquals(4, reloadCounts[flowStartedByAlice])
            assertEquals(5, reloadCounts[ReloadFromCheckpointResponder.flowId])
        }
    }

    @Test(timeout = 300_000)
    fun `flow will not reload from its checkpoint after suspending when reloadCheckpointAfterSuspend is false`() {
        val reloadCounts = mutableMapOf<StateMachineRunId, Int>()
        FlowStateMachineImpl.onReloadFlowFromCheckpoint = { id ->
            reloadCounts.compute(id) { _, value -> value?.plus(1) ?: 1 }
        }
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {

            val nodeAHandle = startNode(
                providedName = ALICE_NAME,
                rpcUsers = listOf(user),
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to false)
            ).getOrThrow()
            val nodeBHandle = startNode(
                providedName = BOB_NAME,
                rpcUsers = listOf(user),
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to false)
            ).getOrThrow()

            val handle = nodeAHandle.rpc.startFlow(::ReloadFromCheckpointFlow, nodeBHandle.nodeInfo.singleIdentity(), false, false)
            val flowStartedByAlice = handle.id
            handle.returnValue.getOrThrow()
            assertEquals(null, reloadCounts[flowStartedByAlice])
            assertEquals(null, reloadCounts[ReloadFromCheckpointResponder.flowId])
        }
    }

    @Test(timeout = 300_000)
    fun `flow will reload from its checkpoint after suspending when reloadCheckpointAfterSuspend is true and throw an error for failed deserialization`() {
        val reloadCounts = mutableMapOf<StateMachineRunId, Int>()
        FlowStateMachineImpl.onReloadFlowFromCheckpoint = { id ->
            reloadCounts.compute(id) { _, value -> value?.plus(1) ?: 1 }
        }
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {

            val nodeAHandle = startNode(
                providedName = ALICE_NAME,
                rpcUsers = listOf(user),
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()
            val nodeBHandle = startNode(
                providedName = BOB_NAME,
                rpcUsers = listOf(user),
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()

            val handle = nodeAHandle.rpc.startFlow(::ReloadFromCheckpointFlow, nodeBHandle.nodeInfo.singleIdentity(), true, false)
            val flowStartedByAlice = handle.id
            assertThatExceptionOfType(StateTransitionException::class.java).isThrownBy { handle.returnValue.getOrThrow() }
                .withCauseExactlyInstanceOf(ReloadFlowFromCheckpointException::class.java)
                .withRootCauseExactlyInstanceOf(CordaRuntimeException::class.java)
                .withMessageContaining(
                    "Could not reload flow from checkpoint. This is likely due to a discrepancy " +
                            "between the serialization and deserialization of an object in the flow's checkpoint"
                )
            assertEquals(4, reloadCounts[flowStartedByAlice])
            assertEquals(4, reloadCounts[ReloadFromCheckpointResponder.flowId])
        }
    }

    @Test(timeout = 300_000)
    fun `counterparty flow will reload from its checkpoint after suspending when reloadCheckpointAfterSuspend is true and throw an error for failed deserialization to other nodes`() {
        val reloadCounts = mutableMapOf<StateMachineRunId, Int>()
        FlowStateMachineImpl.onReloadFlowFromCheckpoint = { id ->
            reloadCounts.compute(id) { _, value -> value?.plus(1) ?: 1 }
        }
        val user = User("mark", "dadada", setOf(Permissions.all()))
        driver(DriverParameters(startNodesInProcess = true, notarySpecs = emptyList())) {

            val nodeAHandle = startNode(
                providedName = ALICE_NAME,
                rpcUsers = listOf(user),
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()
            val nodeBHandle = startNode(
                providedName = BOB_NAME,
                rpcUsers = listOf(user),
                customOverrides = mapOf(NodeConfiguration::reloadCheckpointAfterSuspend.name to true)
            ).getOrThrow()

            val handle = nodeAHandle.rpc.startFlow(::ReloadFromCheckpointFlow, nodeBHandle.nodeInfo.singleIdentity(), false, true)
            val flowStartedByAlice = handle.id
            assertFailsWith<UnexpectedFlowEndException> { handle.returnValue.getOrThrow() }
            assertEquals(4, reloadCounts[flowStartedByAlice])
            assertEquals(4, reloadCounts[ReloadFromCheckpointResponder.flowId])
        }
    }
}

fun isQuasarAgentSpecified(): Boolean {
    val jvmArgs = ManagementFactory.getRuntimeMXBean().inputArguments
    return jvmArgs.any { it.startsWith("-javaagent:") && it.contains("quasar") }
}

class ExceptionToCauseFiniteRetry : ConstraintViolationException("Faked violation", SQLException("Fake"), "Fake name")

@StartableByRPC
@InitiatingFlow
class InitiatorFlow(private val sessionsCount: Int, private val iterationsCount: Int, private val other: Party) : FlowLogic<Any>() {
    companion object {
        object FIRST_STEP : ProgressTracker.Step("Step one")

        fun tracker() = ProgressTracker(FIRST_STEP)

        val seen: MutableSet<Visited> = Collections.synchronizedSet(HashSet<Visited>())

        fun visit(sessionNum: Int, iterationNum: Int, step: Step) {
            val visited = Visited(sessionNum, iterationNum, step)
            if (visited !in seen) {
                seen += visited
                throw FlowTimeoutException()
            }
        }
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): Any {
        progressTracker.currentStep = FIRST_STEP
        var received: Any? = null
        visit(-1, -1, Step.First)
        for (sessionNum in 1..sessionsCount) {
            visit(sessionNum, -1, Step.BeforeInitiate)
            val session = initiateFlow(other)
            visit(sessionNum, -1, Step.AfterInitiate)
            session.send(SessionInfo(sessionNum, iterationsCount))
            visit(sessionNum, -1, Step.AfterInitiateSendReceive)
            for (iteration in 1..iterationsCount) {
                visit(sessionNum, iteration, Step.BeforeSend)
                logger.info("A Sending $sessionNum:$iteration")
                session.send("$sessionNum:$iteration")
                visit(sessionNum, iteration, Step.AfterSend)
                received = session.receive<Any>().unwrap { it }
                visit(sessionNum, iteration, Step.AfterReceive)
                logger.info("A Got $sessionNum:$iteration")
            }
            doSleep()
        }
        return received!!
    }

    // This non-flow-friendly sleep triggered a bug with session end messages and non-retryable checkpoints.
    private fun doSleep() {
        Thread.sleep(2000)
    }
}

@InitiatedBy(InitiatorFlow::class)
class InitiatedFlow(val session: FlowSession) : FlowLogic<Any>() {
    companion object {
        object FIRST_STEP : ProgressTracker.Step("Step one")

        fun tracker() = ProgressTracker(FIRST_STEP)

        val seen: MutableSet<Visited> = Collections.synchronizedSet(HashSet<Visited>())

        fun visit(sessionNum: Int, iterationNum: Int, step: Step) {
            val visited = Visited(sessionNum, iterationNum, step)
            if (visited !in seen) {
                seen += visited
                throw FlowTimeoutException()
            }
        }
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call() {
        progressTracker.currentStep = FIRST_STEP
        visit(-1, -1, Step.AfterInitiate)
        val sessionInfo = session.receive<SessionInfo>().unwrap { it }
        visit(sessionInfo.sessionNum, -1, Step.AfterInitiateSendReceive)
        for (iteration in 1..sessionInfo.iterationsCount) {
            visit(sessionInfo.sessionNum, iteration, Step.BeforeReceive)
            val got = session.receive<Any>().unwrap { it }
            visit(sessionInfo.sessionNum, iteration, Step.AfterReceive)
            logger.info("B Got $got")
            logger.info("B Sending $got")
            visit(sessionInfo.sessionNum, iteration, Step.BeforeSend)
            session.send(got)
            visit(sessionInfo.sessionNum, iteration, Step.AfterSend)
        }
    }
}

@CordaSerializable
data class SessionInfo(val sessionNum: Int, val iterationsCount: Int)

enum class Step { First, BeforeInitiate, AfterInitiate, AfterInitiateSendReceive, BeforeSend, AfterSend, BeforeReceive, AfterReceive }

data class Visited(val sessionNum: Int, val iterationNum: Int, val step: Step)

class BrokenMap<K, V>(delegate: MutableMap<K, V> = mutableMapOf()) : MutableMap<K, V> by delegate {
    override fun put(key: K, value: V): V? = throw IllegalStateException("Broken on purpose")
}

@StartableByRPC
class RetryFlow() : FlowLogic<String>(), IdempotentFlow {
    companion object {
        object FIRST_STEP : ProgressTracker.Step("Step one")

        fun tracker() = ProgressTracker(FIRST_STEP)
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): String {
        progressTracker.currentStep = FIRST_STEP
        throw ExceptionToCauseFiniteRetry()
    }
}

@StartableByRPC
class AsyncRetryFlow() : FlowLogic<String>(), IdempotentFlow {
    companion object {
        object FIRST_STEP : ProgressTracker.Step("Step one")

        fun tracker() = ProgressTracker(FIRST_STEP)

        val deduplicationIds = mutableSetOf<String>()
    }

    class RecordDeduplicationId : FlowExternalAsyncOperation<String> {
        override fun execute(deduplicationId: String): CompletableFuture<String> {
            val dedupeIdIsNew = deduplicationIds.add(deduplicationId)
            if (dedupeIdIsNew) {
                throw ExceptionToCauseFiniteRetry()
            }
            return CompletableFuture.completedFuture(deduplicationId)
        }
    }

    override val progressTracker = tracker()

    @Suspendable
    override fun call(): String {
        progressTracker.currentStep = FIRST_STEP
        await(RecordDeduplicationId())
        return "Result"
    }
}

@StartableByRPC
class ThrowingFlow() : FlowLogic<String>(), IdempotentFlow {
    companion object {
        object FIRST_STEP : ProgressTracker.Step("Step one")

        fun tracker() = ProgressTracker(FIRST_STEP)
    }

    override val progressTracker = tracker()

    init {
        throw IllegalStateException("This flow can never be ")
    }

    @Suspendable
    override fun call(): String {
        progressTracker.currentStep = FIRST_STEP
        return "Result"
    }
}

@StartableByRPC
@InitiatingFlow
class TransientConnectionFailureFlow(private val party: Party) : FlowLogic<Unit>() {
    companion object {
        // start negative due to where it is incremented
        var retryCount = -1
    }

    @Suspendable
    override fun call() {
        initiateFlow(party).send("hello there")
        // checkpoint will restart the flow after the send
        retryCount += 1
        throw SQLTransientConnectionException("Connection is not available")
    }
}

@InitiatedBy(TransientConnectionFailureFlow::class)
class TransientConnectionFailureResponder(private val session: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        session.receive<String>().unwrap { it }
    }
}

@StartableByRPC
@InitiatingFlow
class WrappedTransientConnectionFailureFlow(private val party: Party) : FlowLogic<Unit>() {
    companion object {
        // start negative due to where it is incremented
        var retryCount = -1
    }

    @Suspendable
    override fun call() {
        initiateFlow(party).send("hello there")
        // checkpoint will restart the flow after the send
        retryCount += 1
        throw IllegalStateException(
            "wrapped error message",
            IllegalStateException("another layer deep", SQLTransientConnectionException("Connection is not available"))
        )
    }
}

@InitiatedBy(WrappedTransientConnectionFailureFlow::class)
class WrappedTransientConnectionFailureResponder(private val session: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        session.receive<String>().unwrap { it }
    }
}

@StartableByRPC
@InitiatingFlow
class GeneralExternalFailureFlow(private val party: Party) : FlowLogic<Unit>() {
    companion object {
        // start negative due to where it is incremented
        var retryCount = -1
    }

    @Suspendable
    override fun call() {
        initiateFlow(party).send("hello there")
        // checkpoint will restart the flow after the send
        retryCount += 1
        throw IllegalStateException("Some user general exception")
    }
}

@InitiatedBy(GeneralExternalFailureFlow::class)
class GeneralExternalFailureResponder(private val session: FlowSession) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        session.receive<String>().unwrap { it }
    }
}

@StartableByRPC
@InitiatingFlow
class ReloadFromCheckpointFlow(
    private val party: Party,
    private val shouldHaveDeserializationError: Boolean,
    private val counterPartyHasDeserializationError: Boolean
) : FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val session = initiateFlow(party)
        session.send(counterPartyHasDeserializationError)
        logger.info("completed the send")
        val s = session.receive<String>().unwrap { it }
        logger.info("received your message = $s")
        sleep(1.seconds)
        val map = if (shouldHaveDeserializationError) {
            BrokenMap(mutableMapOf("i dont want" to "this to work"))
        } else {
            mapOf("i dont want" to "this to work")
        }
        session.sendAndReceive<String>("hey I made it this far")
    }
}

@InitiatedBy(ReloadFromCheckpointFlow::class)
class ReloadFromCheckpointResponder(private val session: FlowSession) : FlowLogic<Unit>() {

    companion object {
        var flowId: StateMachineRunId? = null
    }

    @Suspendable
    override fun call() {
        flowId = runId
        val counterPartyHasDeserializationError = session.receive<Boolean>().unwrap { it }
        logger.info("completed the receive = $counterPartyHasDeserializationError")
        session.send("hello there 12312311")
        logger.info("completed the send 2")
        sleep(1.seconds)
        val map = if (counterPartyHasDeserializationError) {
            BrokenMap(mutableMapOf("i dont want" to "this to work"))
        } else {
            mapOf("i dont want" to "this to work")
        }
        session.receive<String>().unwrap { it }
        session.send("sending back a message")
    }
}

@StartableByRPC
class GetCheckpointNumberOfStatusFlow(private val flowStatus: Checkpoint.FlowStatus) : FlowLogic<Long>() {
    override fun call(): Long {
        val sqlStatement =
            "select count(*) " +
                    "from node_checkpoints " +
                    "where status = ${flowStatus.ordinal} " +
                    "and flow_id != '${runId.uuid}' " // don't count in the checkpoint of the current flow

        return serviceHub.jdbcSession().prepareStatement(sqlStatement).use { ps ->
            ps.executeQuery().use { rs ->
                rs.next()
                rs.getLong(1)
            }
        }
    }
}