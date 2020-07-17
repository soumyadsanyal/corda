package net.corda.node.services.bn

import net.corda.core.internal.uncheckedCast
import net.corda.core.node.services.bn.BusinessNetworksServiceInternal
import net.corda.core.serialization.SingletonSerializeAsToken

class NodeBusinessNetworksServiceInternal : BusinessNetworksServiceInternal, SingletonSerializeAsToken() {

    private lateinit var service: BusinessNetworksServiceInternal

    fun start(classLoader: ClassLoader, serviceClassName: String) {
        val serviceClass = classLoader.loadClass(serviceClassName)
        service = uncheckedCast(serviceClass.getConstructor().newInstance())
    }
}