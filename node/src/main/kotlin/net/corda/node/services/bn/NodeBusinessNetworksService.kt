package net.corda.node.services.bn

import net.corda.core.internal.uncheckedCast
import net.corda.core.node.services.bn.BusinessNetworkGroup
import net.corda.core.node.services.bn.BusinessNetworkMembership
import net.corda.core.node.services.bn.BusinessNetworksService
import net.corda.core.serialization.SingletonSerializeAsToken

class NodeBusinessNetworksService : BusinessNetworksService, SingletonSerializeAsToken() {

    private lateinit var service: BusinessNetworksService

    fun start(classLoader: ClassLoader, serviceClassName: String) {
        val serviceClass = classLoader.loadClass(serviceClassName)
        service = uncheckedCast(serviceClass.getConstructor().newInstance())
    }

    override fun getAllBusinessNetworkIds(): List<String> = service.getAllBusinessNetworkIds()

    override fun getAllMemberships(): List<BusinessNetworkMembership> = service.getAllMemberships()

    override fun getAllGroups(): List<BusinessNetworkGroup> = service.getAllGroups()
}