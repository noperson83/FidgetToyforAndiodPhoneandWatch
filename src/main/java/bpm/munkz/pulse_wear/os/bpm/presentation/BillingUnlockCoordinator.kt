package bpm.munkz.pulse_wear.os.bpm.presentation

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams

internal class BillingUnlockCoordinator(
    context: Context,
    private val productIds: Set<String>,
    private val saleProductIds: Set<String> = productIds,
    private val onProductOwned: (String) -> Unit,
    private val onOwnedProductsRefreshed: (Set<String>) -> Unit = {},
    private val consumableProductIds: Set<String> = emptySet(),
    private val onProductConsumed: (String) -> Unit = onProductOwned,
) {
    private val appContext = context.applicationContext
    private val productDetailsById = mutableMapOf<String, ProductDetails>()
    private var ownedProductIds: Set<String> = emptySet()
    private var pendingProductIds: Set<String> = emptySet()
    private val purchaseQueryGeneration = OwnershipQueryGeneration()
    private var purchaseFlowInProgress = false

    var connected by mutableStateOf(false)
        private set
    var ownershipCheckComplete by mutableStateOf(false)
        private set
    var statusText by mutableStateOf("Connecting to Play")
        private set
    private var connecting = false

    private val purchasesUpdatedListener = PurchasesUpdatedListener { billingResult, purchases ->
        when (billingResult.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                if (purchases.isNullOrEmpty()) {
                    purchaseFlowInProgress = false
                    statusText = "Purchase complete"
                } else {
                    // A live Play purchase/promo response is newer than any ownership
                    // snapshot already in flight. Do not let an older empty query revoke it.
                    purchaseQueryGeneration.invalidate()
                    processPurchases(purchases)
                }
            }
            BillingClient.BillingResponseCode.USER_CANCELED -> {
                purchaseFlowInProgress = false
                statusText = "Purchase canceled"
            }
            else -> {
                purchaseFlowInProgress = false
                statusText = "Play purchase error ${billingResult.responseCode}"
            }
        }
    }

    private val billingClient = BillingClient.newBuilder(appContext)
        .setListener(purchasesUpdatedListener)
        .enableAutoServiceReconnection()
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder()
                .enableOneTimeProducts()
                .build(),
        )
        .build()

    fun start() {
        if (billingClient.isReady) {
            connected = true
            queryProducts()
            queryPurchases()
            return
        }
        if (connecting) return

        connecting = true
        statusText = "Connecting to Play"
        billingClient.startConnection(
            object : BillingClientStateListener {
                override fun onBillingSetupFinished(billingResult: BillingResult) {
                    connecting = false
                    connected = billingResult.responseCode == BillingClient.BillingResponseCode.OK
                    if (connected) {
                        statusText = "Checking purchases"
                        queryProducts()
                        queryPurchases()
                    } else {
                        statusText = "Play unavailable ${billingResult.responseCode}"
                    }
                }

                override fun onBillingServiceDisconnected() {
                    connecting = false
                    connected = false
                    statusText = "Play disconnected"
                }
            },
        )
    }

    fun stop() {
        connecting = false
        purchaseQueryGeneration.invalidate()
        ownershipCheckComplete = false
        purchaseFlowInProgress = false
        if (billingClient.isReady) {
            billingClient.endConnection()
        }
    }

    fun refreshPurchases() {
        if (billingClient.isReady) {
            connected = true
            statusText = "Checking purchases"
            queryProducts()
            queryPurchases()
        } else {
            start()
        }
    }

    fun canBuy(productId: String): Boolean {
        return PurchaseOfferState(
            connected = connected,
            ownershipCheckComplete = ownershipCheckComplete,
            purchaseFlowInProgress = purchaseFlowInProgress,
            hasRecognizedOwnership = ownedProductIds.isNotEmpty(),
            hasPendingPurchase = pendingProductIds.isNotEmpty(),
            productReady = productDetailsById.containsKey(productId),
        ).canOfferPurchase()
    }

    fun formattedPrice(productId: String): String? {
        return productDetailsById[productId]
            ?.oneTimePurchaseOfferDetailsList
            ?.firstOrNull()
            ?.formattedPrice
    }

    fun buy(activity: Activity?, productId: String) {
        if (!canBuy(productId)) {
            statusText = if (!ownershipCheckComplete) {
                "Checking purchases"
            } else {
                "Purchase unavailable"
            }
            refreshPurchases()
            return
        }
        val hostActivity = activity
        if (hostActivity == null) {
            statusText = "Open app on watch"
            return
        }

        val productDetails = productDetailsById[productId]
        if (productDetails == null) {
            statusText = "Product not ready"
            queryProducts()
            return
        }

        val productDetailsParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(productDetails)
        productDetails.oneTimePurchaseOfferDetailsList?.firstOrNull()?.offerToken?.let { offerToken ->
            productDetailsParamsBuilder.setOfferToken(offerToken)
        }
        val productDetailsParams = productDetailsParamsBuilder.build()
        val billingFlowParams = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(listOf(productDetailsParams))
            .build()
        val result = billingClient.launchBillingFlow(hostActivity, billingFlowParams)
        if (result.responseCode == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            purchaseFlowInProgress = false
            statusText = "Restoring purchase"
            queryPurchases()
        } else if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            purchaseFlowInProgress = false
            statusText = "Play launch error ${result.responseCode}"
        } else {
            purchaseFlowInProgress = true
        }
    }

    private fun queryProducts() {
        if (!billingClient.isReady || productIds.isEmpty()) return

        val products = productIds.map { productId ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(productId)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(products)
            .build()
        billingClient.queryProductDetailsAsync(params) { billingResult, productDetailsResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                productDetailsById.clear()
                productDetailsResult.productDetailsList.forEach { productDetails ->
                    productDetailsById[productDetails.productId] = productDetails
                }
                statusText = when (
                    purchaseCatalogState(productDetailsById.keys, saleProductIds)
                ) {
                    PurchaseCatalogState.MissingAllProducts -> "Product not in Play Console"
                    PurchaseCatalogState.MissingSaleProduct -> "Pulse Pro product not ready"
                    PurchaseCatalogState.Ready -> "Ready"
                }
            } else {
                statusText = "Product error ${billingResult.responseCode}"
            }
        }
    }

    private fun queryPurchases() {
        if (!billingClient.isReady) return
        val queryGeneration = purchaseQueryGeneration.begin()
        ownershipCheckComplete = false

        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { billingResult, purchases ->
            if (!purchaseQueryGeneration.isCurrent(queryGeneration)) return@queryPurchasesAsync
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                ownedProductIds = purchases
                    .asSequence()
                    .filter { purchase -> purchase.purchaseState == Purchase.PurchaseState.PURCHASED }
                    .flatMap { purchase -> purchase.products.asSequence() }
                    .filter { productId -> productId in productIds }
                    .toSet()
                pendingProductIds = purchases
                    .asSequence()
                    .filter { purchase -> purchase.purchaseState == Purchase.PurchaseState.PENDING }
                    .flatMap { purchase -> purchase.products.asSequence() }
                    .filter { productId -> productId in productIds }
                    .toSet()
                ownershipCheckComplete = true
                onOwnedProductsRefreshed(ownedProductIds)
                processPurchases(purchases)
            } else {
                ownershipCheckComplete = false
                statusText = "Purchase check error ${billingResult.responseCode}"
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>) {
        purchases.forEach { purchase ->
            val ownedProductId = purchase.products.firstOrNull { productId -> productId in productIds }
                ?: return@forEach
            when (purchase.purchaseState) {
                Purchase.PurchaseState.PURCHASED -> {
                    purchaseFlowInProgress = false
                    ownedProductIds = ownedProductIds + ownedProductId
                    pendingProductIds = pendingProductIds - ownedProductId
                    if (ownedProductId in consumableProductIds) {
                        consumeOrComplete(purchase, ownedProductId)
                    } else {
                        acknowledgeOrUnlock(purchase, ownedProductId)
                    }
                }
                Purchase.PurchaseState.PENDING -> {
                    purchaseFlowInProgress = true
                    pendingProductIds = pendingProductIds + ownedProductId
                    statusText = "Purchase pending"
                }
                else -> Unit
            }
        }
    }

    private fun consumeOrComplete(purchase: Purchase, productId: String) {
        val params = ConsumeParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.consumeAsync(params) { billingResult, _ ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                ownedProductIds = ownedProductIds - productId
                pendingProductIds = pendingProductIds - productId
                purchaseFlowInProgress = false
                onProductConsumed(productId)
                statusText = "Thanks"
            } else {
                statusText = "Consume error ${billingResult.responseCode}"
            }
        }
    }

    private fun acknowledgeOrUnlock(purchase: Purchase, productId: String) {
        if (purchase.isAcknowledged) {
            onProductOwned(productId)
            statusText = "Unlocked"
            return
        }

        val params = AcknowledgePurchaseParams.newBuilder()
            .setPurchaseToken(purchase.purchaseToken)
            .build()
        billingClient.acknowledgePurchase(params) { billingResult ->
            if (billingResult.responseCode == BillingClient.BillingResponseCode.OK) {
                onProductOwned(productId)
                statusText = "Unlocked"
            } else {
                statusText = "Acknowledge error ${billingResult.responseCode}"
            }
        }
    }
}
