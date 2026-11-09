package com.whitertc.vpn

enum class PhysicalNetworkTransition {
    NONE,
    AVAILABLE,
    CHANGED,
    UNAVAILABLE,
}

enum class PhysicalNetworkPauseReason {
    AIRPLANE_MODE,
    OFFLINE,
}

fun physicalNetworkPauseReason(airplaneModeOn: Boolean): PhysicalNetworkPauseReason =
    if (airplaneModeOn) PhysicalNetworkPauseReason.AIRPLANE_MODE else PhysicalNetworkPauseReason.OFFLINE

fun isEligiblePhysicalNetwork(
    hasInternet: Boolean,
    notVpn: Boolean,
    wifi: Boolean,
    cellular: Boolean,
    ethernet: Boolean
): Boolean = hasInternet && notVpn && (wifi || cellular || ethernet)

class PhysicalNetworkTracker<T> {
    private val active = mutableSetOf<T>()
    private val lock = Any()

    fun update(network: T, usable: Boolean): PhysicalNetworkTransition {
        synchronized(lock) {
            val wasAvailable = active.isNotEmpty()
            val changed = if (usable) active.add(network) else active.remove(network)
            val isAvailable = active.isNotEmpty()
            return when {
                !wasAvailable && isAvailable -> PhysicalNetworkTransition.AVAILABLE
                wasAvailable && !isAvailable -> PhysicalNetworkTransition.UNAVAILABLE
                changed && wasAvailable && isAvailable -> PhysicalNetworkTransition.CHANGED
                else -> PhysicalNetworkTransition.NONE
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            active.clear()
        }
    }

    fun hasUsableNetwork(): Boolean = synchronized(lock) {
        active.isNotEmpty()
    }

    fun isUsable(network: T): Boolean = synchronized(lock) {
        network in active
    }

    fun getActiveNetworks(): List<T> = synchronized(lock) {
        active.toList()
    }
}
