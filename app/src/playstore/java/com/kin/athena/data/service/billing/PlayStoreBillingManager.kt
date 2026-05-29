/*
 * Copyright (C) 2025-2026 Vexzure
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.kin.athena.data.service.billing

import android.app.Activity
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.kin.athena.core.logging.Logger
import javax.inject.Inject

class PlayStoreBillingManager
  @Inject
  constructor(
    private val context: Activity,
  ) : BillingInterface,
    PurchasesUpdatedListener {
    private lateinit var billingClient: BillingClient
    private val productDetailsMap = mutableMapOf<String, ProductDetails>()
    private var pendingSuccessCallback: (() -> Unit)? = null
    private val ownedProducts = mutableSetOf<String>()

    init {
      setupBillingClient()
    }

    private fun setupBillingClient() {
      billingClient =
        BillingClient
          .newBuilder(context)
          .enableAutoServiceReconnection()
          .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build(),
          ).setListener(this)
          .build()
      startConnection()
    }

    private fun startConnection() {
      billingClient.startConnection(
        object : BillingClientStateListener {
          override fun onBillingSetupFinished(result: BillingResult) {
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
              Logger.info("Billing client setup successful")
              queryProducts()
            } else {
              Logger.error("Setup failed: ${result.responseCode}")
              retryConnection()
            }
          }

          override fun onBillingServiceDisconnected() {
            Logger.error("Service disconnected")
            retryConnection()
          }
        },
      )
    }

    private fun retryConnection() {
      Handler(Looper.getMainLooper()).postDelayed({ startConnection() }, 5000)
    }

    private fun queryProducts() {
      val products =
        listOf(
          QueryProductDetailsParams.Product
            .newBuilder()
            .setProductId("all_features")
            .setProductType(BillingClient.ProductType.INAPP)
            .build(),
          QueryProductDetailsParams.Product
            .newBuilder()
            .setProductId("packet_logs")
            .setProductType(BillingClient.ProductType.INAPP)
            .build(),
          QueryProductDetailsParams.Product
            .newBuilder()
            .setProductId("notify_on_install")
            .setProductType(BillingClient.ProductType.INAPP)
            .build(),
          QueryProductDetailsParams.Product
            .newBuilder()
            .setProductId("custom_blocklist")
            .setProductType(BillingClient.ProductType.INAPP)
            .build(),
          QueryProductDetailsParams.Product
            .newBuilder()
            .setProductId("speed_notification")
            .setProductType(BillingClient.ProductType.INAPP)
            .build(),
        )
      billingClient.queryProductDetailsAsync(
        QueryProductDetailsParams.newBuilder().setProductList(products).build(),
      ) { billingResult, productDetailsList ->
        if (
          billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
          productDetailsList != null
        ) {
          productDetailsList.productDetailsList.forEach { pd ->
            productDetailsMap[pd.productId] = pd
            val price = pd.oneTimePurchaseOfferDetails?.formattedPrice ?: "Price not available"
            Logger.info("Product fetched: ${pd.productId} - Price: $price")
          }
        } else {
          Logger.error("Failed product fetch: ${billingResult.responseCode}")
        }
      }
    }

    override fun checkExistingPurchases(onPremiumOwned: () -> Unit) {
      if (!billingClient.isReady) {
        Logger.error("Billing client not ready for purchase check")
        return
      }

      billingClient.queryPurchasesAsync(
        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
      ) { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
          purchases != null
        ) {
          // Clear and rebuild owned products cache
          ownedProducts.clear()
          purchases.forEach { purchase ->
            if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
              ownedProducts.addAll(purchase.products)
              Logger.info("Owned product: ${purchase.products}")
            }
          }

          val hasPremium = ownedProducts.contains("all_features")
          if (hasPremium) {
            Logger.info("User already owns premium features")
            onPremiumOwned()
          } else {
            Logger.info("User owns individual features: $ownedProducts")
          }
        } else {
          Logger.error("Error checking existing purchases: ${billingResult.responseCode}")
        }
      }
    }

    override fun showPurchaseDialog(
      productId: String,
      onSuccess: () -> Unit,
    ) {
      if (!billingClient.isReady) {
        Logger.error("Billing client not ready")
        return
      }
      pendingSuccessCallback = onSuccess

      billingClient.queryPurchasesAsync(
        QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build(),
      ) { billingResult, purchases ->
        if (billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
          purchases != null
        ) {
          if (
            purchases.any {
              it.products.contains(productId) &&
                it.purchaseState == Purchase.PurchaseState.PURCHASED
            }
          ) {
            Logger.info("$productId already owned")
            pendingSuccessCallback?.invoke()
            pendingSuccessCallback = null
            return@queryPurchasesAsync
          }
        } else {
          Logger.error("Error querying purchases: ${billingResult.responseCode}")
        }

        productDetailsMap[productId]?.let { launchBillingFlow(it) }
          ?: refetchProduct(productId) { newPd ->
            newPd?.let { launchBillingFlow(it) }
              ?: Logger.error("Missing ProductDetails for $productId")
          }
      }
    }

    override fun isReady(): Boolean = billingClient.isReady

    private fun launchBillingFlow(details: ProductDetails) {
      val params =
        BillingFlowParams
          .newBuilder()
          .setProductDetailsParamsList(
            listOf(
              BillingFlowParams.ProductDetailsParams
                .newBuilder()
                .setProductDetails(details)
                .build(),
            ),
          ).build()
      val result = billingClient.launchBillingFlow(context, params)
      if (result.responseCode == BillingClient.BillingResponseCode.OK) {
        Logger.info("Flow launched for ${details.productId}")
      } else {
        Logger.error("Launch failed: code=${result.responseCode} subCode=${result.responseCode}")
        pendingSuccessCallback = null
      }
    }

    private fun refetchProduct(
      productId: String,
      callback: (ProductDetails?) -> Unit,
    ) {
      val prod =
        QueryProductDetailsParams.Product
          .newBuilder()
          .setProductId(productId)
          .setProductType(BillingClient.ProductType.INAPP)
          .build()
      billingClient.queryProductDetailsAsync(
        QueryProductDetailsParams.newBuilder().setProductList(listOf(prod)).build(),
      ) { billingResult, list ->
        if (
          billingResult.responseCode == BillingClient.BillingResponseCode.OK &&
          list.productDetailsList.isNotEmpty()
        ) {
          callback(list.productDetailsList.first())
        } else {
          Logger.error("Refetch failed for $productId: ${billingResult.responseCode}")
          callback(null)
        }
      }
    }

    override fun onPurchasesUpdated(
      billingResult: BillingResult,
      purchases: MutableList<Purchase>?,
    ) {
      when (billingResult.responseCode) {
        BillingClient.BillingResponseCode.OK -> {
          purchases?.forEach { handlePurchase(it) }
        }

        BillingClient.BillingResponseCode.USER_CANCELED -> {
          Logger.info("User canceled purchase")
          pendingSuccessCallback = null
        }

        else -> {
          Logger.error("Purchases update failed: ${billingResult.responseCode}")
          pendingSuccessCallback = null
        }
      }
    }

    private fun handlePurchase(purchase: Purchase) {
      if (purchase.purchaseState == Purchase.PurchaseState.PURCHASED) {
        Logger.info("Purchase: ${purchase.products}")
        // Add to owned products cache
        ownedProducts.addAll(purchase.products)
        Logger.info("Updated owned products cache: $ownedProducts")

        if (!purchase.isAcknowledged) {
          billingClient.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build(),
          ) { ack ->
            if (ack.responseCode == BillingClient.BillingResponseCode.OK) {
              pendingSuccessCallback?.invoke()
              pendingSuccessCallback = null
            }
          }
        } else {
          pendingSuccessCallback?.invoke()
          pendingSuccessCallback = null
        }
      }
    }

    override fun getProductPrice(productId: String): String? {
      val price = productDetailsMap[productId]?.oneTimePurchaseOfferDetails?.formattedPrice
      Logger.info("Getting price for $productId: $price")
      return price
    }

    override fun getAllProductPrices(): Map<String, String> {
      val prices =
        productDetailsMap
          .mapNotNull { (id, details) ->
            details.oneTimePurchaseOfferDetails?.formattedPrice?.let { price -> id to price }
          }.toMap()
      Logger.info("All product prices: $prices")
      return prices
    }

    override fun isProductOwned(productId: String): Boolean {
      val isOwned = ownedProducts.contains(productId) || ownedProducts.contains("all_features")
      Logger.info("Checking if $productId is owned: $isOwned (owned products: $ownedProducts)")
      return isOwned
    }

    override fun getDiscountText(): String {
      // Play Store variant uses static discount text
      return "20% OFF"
    }
  }
