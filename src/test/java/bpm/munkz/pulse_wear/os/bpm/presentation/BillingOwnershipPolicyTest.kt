package bpm.munkz.pulse_wear.os.bpm.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingOwnershipPolicyTest {
    private val oneDollarDonation = "fidget_donation_1"
    private val threeDollarDonation = "fidget_donation_3"

    private val ready = PurchaseOfferState(
        connected = true,
        ownershipCheckComplete = true,
        purchaseFlowInProgress = false,
        hasRecognizedOwnership = false,
        hasPendingPurchase = false,
        productReady = true,
    )

    @Test
    fun buyStaysDisabledUntilOwnershipCheckFinishes() {
        assertFalse(ready.copy(ownershipCheckComplete = false).canOfferPurchase())
        assertTrue(ready.canOfferPurchase())
    }

    @Test
    fun legacyOrCurrentOwnershipPreventsSecondCharge() {
        assertFalse(ready.copy(hasRecognizedOwnership = true).canOfferPurchase())
    }

    @Test
    fun pendingOrActiveFlowPreventsDuplicatePurchase() {
        assertFalse(ready.copy(hasPendingPurchase = true).canOfferPurchase())
        assertFalse(ready.copy(purchaseFlowInProgress = true).canOfferPurchase())
    }

    @Test
    fun olderOwnershipCallbackCannotReplaceNewerResult() {
        val generation = OwnershipQueryGeneration()
        val older = generation.begin()
        val newer = generation.begin()

        assertFalse(generation.isCurrent(older))
        assertTrue(generation.isCurrent(newer))

        generation.invalidate()
        assertFalse(generation.isCurrent(newer))
    }

    @Test
    fun incompleteDonationCatalogDoesNotReportReady() {
        assertEquals(
            PurchaseCatalogState.MissingSaleProduct,
            purchaseCatalogState(
                availableProductIds = setOf(oneDollarDonation),
                saleProductIds = setOf(oneDollarDonation, threeDollarDonation),
            ),
        )
        assertEquals(
            PurchaseCatalogState.Ready,
            purchaseCatalogState(
                availableProductIds = setOf(oneDollarDonation, threeDollarDonation),
                saleProductIds = setOf(oneDollarDonation, threeDollarDonation),
            ),
        )
    }
}
