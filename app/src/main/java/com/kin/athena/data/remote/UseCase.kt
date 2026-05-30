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

package com.kin.athena.data.remote

import com.google.gson.annotations.SerializedName
import com.kin.athena.core.logging.Logger
import com.kin.athena.core.utils.Error
import com.kin.athena.core.utils.Result
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.POST
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface LicenseApi {
  @FormUrlEncoded
  @POST("api/licenses.php")
  suspend fun verifyLicense(
    @Field("action") action: String = "verify",
    @Field("key") key: String,
  ): LicenseResponse
}

interface PriceApi {
  @GET("api/stripe/price")
  suspend fun getPrice(
    @retrofit2.http.Query("currency") currency: String? = null,
    @retrofit2.http.Query("product") product: String? = null,
  ): PriceResponse
}

data class LicenseResponse(
  val success: Boolean,
  val error: String?,
  val valid: Boolean? = null,
  val license: LicenseData? = null,
)

data class LicenseData(
  val type: String,
  val status: String,
  @SerializedName("expires_at") val expiresAt: String?,
  val activations: ActivationData,
)

data class ActivationData(
  val max: String,
  val current: Int,
)

data class PriceResponse(
  val amount: Long,
  val currency: String,
  val formatted: String,
  val discountText: String? = null,
)

class LicenseRepositoryImpl
  @Inject
  constructor(
    private val api: LicenseApi,
  ) : LicenseRepository {
    override suspend fun verifyLicense(key: String): LicenseResponse =
      try {
        api.verifyLicense(key = key)
      } catch (e: IOException) {
        Logger.error("Network error verifying license: ${e.message}")
        LicenseResponse(
          success = false,
          error = "Network error: ${e.message}",
          valid = false,
          license = null,
        )
      } catch (e: HttpException) {
        Logger.error("HTTP error verifying license: ${e.code()} - ${e.message}")
        LicenseResponse(
          success = false,
          error = "Server error: ${e.code()} - ${e.message}",
          valid = false,
          license = null,
        )
      } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Logger.error("Unexpected error verifying license: ${e.message}")
        LicenseResponse(
          success = false,
          error = "Unexpected error: ${e.message}",
          valid = false,
          license = null,
        )
      }
  }

class PriceRepositoryImpl
  @Inject
  constructor(
    private val api: PriceApi,
  ) : PriceRepository {
    override suspend fun getPrice(
      currency: String?,
      product: String?,
    ): PriceResponse =
      try {
        api.getPrice(currency = currency, product = product)
      } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Logger.error("Error fetching price: ${e.message}")
        // Return fallback price based on product
        val fallbackAmount = if (product == "premium") 499 else 200
        val fallbackFormatted = if (product == "premium") "$4.99" else "$2.00"
        PriceResponse(
          amount = fallbackAmount.toLong(),
          currency = "usd",
          formatted = fallbackFormatted,
        )
      }
  }

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {
  @Provides
  @Singleton
  fun provideOkHttpClient(): OkHttpClient =
    OkHttpClient
      .Builder()
      .connectTimeout(30, TimeUnit.SECONDS) // Increase connection timeout
      .readTimeout(30, TimeUnit.SECONDS) // Increase read timeout
      .writeTimeout(30, TimeUnit.SECONDS) // Increase write timeout
      .build()

  @Provides
  @Singleton
  fun provideRetrofit(okHttpClient: OkHttpClient): Retrofit =
    Retrofit
      .Builder()
      .baseUrl("https://admin.easyapps.me/") // Verify this URL is correct
      .client(okHttpClient)
      .addConverterFactory(GsonConverterFactory.create())
      .build()

  @Provides
  @Singleton
  fun provideLicenseRepository(api: LicenseApi): LicenseRepository = LicenseRepositoryImpl(api)

  @Provides
  @Singleton
  fun provideLicenseApi(retrofit: Retrofit): LicenseApi = retrofit.create(LicenseApi::class.java)

  @Provides
  @Singleton
  fun provideVerifyLicenseUseCase(repository: LicenseRepository): VerifyLicenseUseCase =
    VerifyLicenseUseCase(repository)

  @Provides
  @Singleton
  fun providePriceApi(okHttpClient: OkHttpClient): PriceApi {
    val retrofit =
      Retrofit
        .Builder()
        .baseUrl("https://api.easyapps.me/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    return retrofit.create(PriceApi::class.java)
  }

  @Provides
  @Singleton
  fun providePriceRepository(api: PriceApi): PriceRepository = PriceRepositoryImpl(api)

  @Provides
  @Singleton
  fun provideGetPriceUseCase(repository: PriceRepository): GetPriceUseCase = GetPriceUseCase(repository)
}

interface LicenseRepository {
  suspend fun verifyLicense(key: String): LicenseResponse
}

interface PriceRepository {
  suspend fun getPrice(
    currency: String? = null,
    product: String? = null,
  ): PriceResponse
}

class VerifyLicenseUseCase
  @Inject
  constructor(
    private val repository: LicenseRepository,
  ) {
    suspend operator fun invoke(key: String): Result<LicenseResponse, Error> =
      try {
        Result.Success(repository.verifyLicense(key))
      } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Result.Failure(Error.ServerError(e.message ?: "Error while verifying license"))
      }
  }

class GetPriceUseCase
  @Inject
  constructor(
    private val repository: PriceRepository,
  ) {
    suspend operator fun invoke(
      currency: String? = null,
      product: String? = null,
    ): Result<PriceResponse, Error> =
      try {
        Result.Success(repository.getPrice(currency = currency, product = product))
      } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
        Result.Failure(Error.ServerError(e.message ?: "Error fetching price"))
      }
  }
