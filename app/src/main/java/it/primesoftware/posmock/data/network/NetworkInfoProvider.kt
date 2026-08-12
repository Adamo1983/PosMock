package it.primesoftware.posmock.data.network

import it.primesoftware.posmock.domain.repository.INetworkInfoProvider
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * IP locale letto dalle interfacce di rete.
 *
 * Si passa da `NetworkInterface` e non dal `WifiManager` perche' il banco di
 * prova non e' per forza in Wi-Fi (capita l'USB tethering o una ethernet via
 * adattatore), e perche' `WifiManager.connectionInfo` e' deprecato dal 31.
 */
class NetworkInfoProvider : INetworkInfoProvider {

    override fun localIpAddress(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()
            .toList()
            .asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList().asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress }
            ?.hostAddress
    }.getOrNull()
}
