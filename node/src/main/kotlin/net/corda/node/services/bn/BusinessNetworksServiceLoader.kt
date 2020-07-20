package net.corda.node.services.bn

import net.corda.core.internal.uncheckedCast
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.bn.BusinessNetworksService
import net.corda.core.serialization.SingletonSerializeAsToken
import net.corda.node.services.config.BusinessNetworksServiceType

class BusinessNetworksServiceLoader(val vaultService: VaultService) : SingletonSerializeAsToken() {

    fun load(classLoader: ClassLoader, serviceType: BusinessNetworksServiceType, serviceClassName: String): BusinessNetworksService {
        val serviceClass = classLoader.loadClass(serviceClassName)
        return when (serviceType) {
            BusinessNetworksServiceType.VAULT -> uncheckedCast(serviceClass.getConstructor(VaultService::class.java).newInstance(vaultService))
        }
    }
}