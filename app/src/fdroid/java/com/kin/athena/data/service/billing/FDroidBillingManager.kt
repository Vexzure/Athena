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
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kin.athena.BuildConfig
import com.kin.athena.core.logging.Logger
import com.kin.athena.data.remote.GetPriceUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

class FDroidBillingManager
  @Inject
  constructor(
    private val context: Activity,
    private val getPriceUseCase: GetPriceUseCase,
  ) : BillingInterface {
    var showKofiDialog by mutableStateOf(false)
      private set

    private var currentOnSuccess: (() -> Unit)? = null
    private val cachedPrices = mutableMapOf<String, String>()
    private var cachedDiscountText: String = ""

    // Map app product IDs to API slugs
    private val productIdToSlug =
      mapOf(
        "all_features" to "premium",
        "packet_logs" to "logs",
        "notify_on_install" to "notify",
        "custom_blocklist" to "dns",
        "speed_notification" to "speed",
      )

    // Map product IDs to Stripe checkout URLs
    private val productIdToStripeUrl =
      mapOf(
        "all_features" to "https://buy.stripe.com/test_00weVe0vCgkd09x8RY3ZK01",
        "packet_logs" to "https://buy.stripe.com/test_9B69AU0vC6JDaOb2tA3ZK02",
        "custom_blocklist" to "https://buy.stripe.com/test_fZuaEYcek7NH09x4BI3ZK03",
        "speed_notification" to "https://buy.stripe.com/test_eVq5kE1zG4Bv4pN6JQ3ZK04",
        "notify_on_install" to "https://buy.stripe.com/test_bJe4gA3HOc3Xe0n2tA3ZK05",
      )

    init {
      CoroutineScope(Dispatchers.IO).launch {
        Logger.info("F-Droid: Fetching prices (API will auto-detect country)")

        productIdToSlug.forEach { (productId, slug) ->
          getPriceUseCase
            .invoke(product = slug)
            .fold(
              ifSuccess = { priceResponse ->
                cachedPrices[productId] = priceResponse.formatted
                // Store discount text from first successful response
                if (cachedDiscountText.isEmpty() && !priceResponse.discountText.isNullOrEmpty()) {
                  cachedDiscountText = priceResponse.discountText
                  Logger.info("F-Droid: Discount text: $cachedDiscountText")
                }
                Logger.info(
                  "F-Droid: Fetched price for $productId ($slug): " +
                    "${priceResponse.formatted} (${priceResponse.currency})",
                )
              },
              ifFailure = { error ->
                Logger.error("F-Droid: Failed to fetch price for $productId: ${error.message}")
                // Set fallback price
                cachedPrices[productId] = if (productId == "all_features") "$4.99" else "$2.00"
              },
            )
        }
      }
    }

    override fun showPurchaseDialog(
      productId: String,
      onSuccess: () -> Unit,
    ) {
      Logger.info("F-Droid: Opening Stripe checkout for $productId")
      openStripeCheckout(productId)
    }

    override fun isReady(): Boolean = true

    override fun getProductPrice(productId: String): String? {
      val price = cachedPrices[productId] ?: if (productId == "all_features") "$4.99" else "$2.00"
      Logger.info("F-Droid: Getting price for $productId: $price")
      return price
    }

    override fun getAllProductPrices(): Map<String, String> {
      Logger.info("F-Droid: All product prices: $cachedPrices")
      return cachedPrices.toMap()
    }

    private fun showKofiFallbackDialog(
      productId: String,
      onSuccess: () -> Unit,
    ) {
      currentOnSuccess = onSuccess
      showKofiDialog = true
    }

    fun dismissKofiDialog() {
      showKofiDialog = false
      currentOnSuccess = null
    }

    fun handleKofiClick() {
      openStripeCheckout("all_features")
      dismissKofiDialog()
    }

    private fun openStripeCheckout(productId: String) {
      try {
        val stripeUrl = productIdToStripeUrl[productId] ?: BuildConfig.KOFI_URL
        if (stripeUrl.isNotEmpty()) {
          val intent = Intent(Intent.ACTION_VIEW, Uri.parse(stripeUrl))
          context.startActivity(intent)
          Logger.info("F-Droid: Opened Stripe checkout for $productId: $stripeUrl")
        } else {
          Logger.error("F-Droid: Stripe URL not configured for $productId")
        }
      } catch (e: Exception) {
        Logger.error("F-Droid: Failed to open Stripe checkout: ${e.message}")
      }
    }

    override fun getDiscountText(): String = cachedDiscountText.ifEmpty { "20% OFF" }

    override fun checkExistingPurchases(onPremiumOwned: () -> Unit) {
      // F-Droid doesn't use Google Play billing, so no purchases to check
      Logger.info("F-Droid: No purchase check needed")
    }

    override fun isProductOwned(productId: String): Boolean {
      // F-Droid doesn't track individual purchases
      Logger.info("F-Droid: No product ownership tracking")
      return false
    }
  }
