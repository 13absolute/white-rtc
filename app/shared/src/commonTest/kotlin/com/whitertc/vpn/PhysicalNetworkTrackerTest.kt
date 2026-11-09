package com.whitertc.vpn

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PhysicalNetworkTrackerTest {

    @Test
    fun duplicateCallbacksProduceOneAvailabilityTransition() {
        val tracker = PhysicalNetworkTracker<String>()
        assertEquals(PhysicalNetworkTransition.AVAILABLE, tracker.update("mobile", true))
        repeat(50) {
            assertEquals(PhysicalNetworkTransition.NONE, tracker.update("mobile", true))
        }
        assertTrue(tracker.hasUsableNetwork())
        assertTrue(tracker.isUsable("mobile"))
    }

    @Test
    fun overlappingPhysicalNetworksSignalTransportChange() {
        val tracker = PhysicalNetworkTracker<String>()
        assertEquals(PhysicalNetworkTransition.AVAILABLE, tracker.update("mobile", true))
        assertEquals(PhysicalNetworkTransition.CHANGED, tracker.update("wifi", true))
        assertEquals(PhysicalNetworkTransition.CHANGED, tracker.update("mobile", false))
        assertEquals(PhysicalNetworkTransition.UNAVAILABLE, tracker.update("wifi", false))
        assertFalse(tracker.hasUsableNetwork())
    }

    @Test
    fun rapidAirplaneCyclesRemainConsistent() {
        val tracker = PhysicalNetworkTracker<String>()
        repeat(100) { i ->
            assertEquals(PhysicalNetworkTransition.AVAILABLE, tracker.update("mobile-$i", true))
            assertEquals(PhysicalNetworkTransition.NONE, tracker.update("mobile-$i", true))
            assertEquals(PhysicalNetworkTransition.UNAVAILABLE, tracker.update("mobile-$i", false))
            assertEquals(PhysicalNetworkTransition.NONE, tracker.update("mobile-$i", false))
        }
    }

    @Test
    fun pauseReasonUsesTheActualAirplaneModeState() {
        assertEquals(
            PhysicalNetworkPauseReason.AIRPLANE_MODE,
            physicalNetworkPauseReason(airplaneModeOn = true)
        )
        assertEquals(
            PhysicalNetworkPauseReason.OFFLINE,
            physicalNetworkPauseReason(airplaneModeOn = false)
        )
    }

    @Test
    fun clearRemovesAllActiveNetworks() {
        val tracker = PhysicalNetworkTracker<String>()
        tracker.update("mobile", true)
        tracker.update("wifi", true)
        assertEquals(2, tracker.getActiveNetworks().size)
        assertTrue(tracker.hasUsableNetwork())

        tracker.clear()
        assertFalse(tracker.hasUsableNetwork())
        assertFalse(tracker.isUsable("mobile"))
        assertFalse(tracker.isUsable("wifi"))
        assertTrue(tracker.getActiveNetworks().isEmpty())
    }

    @Test
    fun eligibilityFilteringRejectsVpnAndNonPhysicalTransports() {
        assertTrue(
            isEligiblePhysicalNetwork(
                hasInternet = true,
                notVpn = true,
                wifi = true,
                cellular = false,
                ethernet = false
            )
        )
        assertTrue(
            isEligiblePhysicalNetwork(
                hasInternet = true,
                notVpn = true,
                wifi = false,
                cellular = true,
                ethernet = false
            )
        )
        assertTrue(
            isEligiblePhysicalNetwork(
                hasInternet = true,
                notVpn = true,
                wifi = false,
                cellular = false,
                ethernet = true
            )
        )
        // VPN must be rejected to prevent routing loops
        assertFalse(
            isEligiblePhysicalNetwork(
                hasInternet = true,
                notVpn = false,
                wifi = true,
                cellular = false,
                ethernet = false
            )
        )
        // Missing internet capability
        assertFalse(
            isEligiblePhysicalNetwork(
                hasInternet = false,
                notVpn = true,
                wifi = true,
                cellular = false,
                ethernet = false
            )
        )
        // No physical transport
        assertFalse(
            isEligiblePhysicalNetwork(
                hasInternet = true,
                notVpn = true,
                wifi = false,
                cellular = false,
                ethernet = false
            )
        )
    }
}
