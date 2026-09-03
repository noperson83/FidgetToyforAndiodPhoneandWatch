package bpm.munkz.pulse_wear.os.bpm.presentation

internal data class PurchaseOfferState(
    val connected: Boolean,
    val ownershipCheckComplete: Boolean,
    val purchaseFlowInProgress: Boolean,
    val hasRecognizedOwnership: Boolean,
    val hasPendingPurchase: Boolean,
    val productReady: Boolean,
)

internal fun PurchaseOfferState.canOfferPurchase(): Boolean {
    return connected &&
        ownershipCheckComplete &&
        !purchaseFlowInProgress &&
        !hasRecognizedOwnership &&
        !hasPendingPurchase &&
        productReady
}

internal enum class PurchaseCatalogState {
    MissingAllProducts,
    MissingSaleProduct,
    Ready,
}

internal fun purchaseCatalogState(
    availableProductIds: Set<String>,
    saleProductIds: Set<String>,
): PurchaseCatalogState {
    return when {
        availableProductIds.isEmpty() -> PurchaseCatalogState.MissingAllProducts
        !availableProductIds.containsAll(saleProductIds) -> PurchaseCatalogState.MissingSaleProduct
        else -> PurchaseCatalogState.Ready
    }
}

/** Tokens make overlapping Play callbacks harmless: only the newest query wins. */
internal class OwnershipQueryGeneration {
    private var current = 0L

    fun begin(): Long = ++current

    fun invalidate() {
        current += 1L
    }

    fun isCurrent(token: Long): Boolean = token == current
}
