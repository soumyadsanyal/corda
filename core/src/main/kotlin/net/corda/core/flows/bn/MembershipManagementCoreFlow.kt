package net.corda.core.flows.bn

import co.paralleluniverse.fibers.Suspendable
import net.corda.core.flows.FlowException
import net.corda.core.flows.FlowLogic
import net.corda.core.node.services.bn.BusinessNetworksService

abstract class MembershipManagementCoreFlow<T> : FlowLogic<T>() {

    protected abstract fun getConcreteImplementationFlow(service: BusinessNetworksService): MembershipManagementFlow<T>

    @Suspendable
    override fun call(): T {
        val service = serviceHub.businessNetworksService ?: throw FlowException("Business Network Service not initialised")
        return subFlow(getConcreteImplementationFlow(service))
    }
}