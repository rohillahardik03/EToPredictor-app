package com.eto.predictor

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// ── Models List Response ──────────────────────────────────────────────
data class ModelResponse(
    val total: Int,
    val models: List<ApiModel>
)

data class ApiModel(
    val rank: Int,
    val id: String,
    val features: String,
    val n_inputs: Int,
    val r2_test: Double,
    val rmse: Double,
    val mae: Double,
    val nse: Double,
    val input_cols: List<String>
)

// ── ETo Prediction Request ────────────────────────────────────────────
data class EToNewRequest(
    val parameters: Map<String, Double>
)

// ── ETo Prediction Response ───────────────────────────────────────────
data class EToModelUsed(
    val id: String,
    val rank: Int,
    val r2_test: Double,
    val rmse: Double,
    val mae: Double,
    val nse: Double,
    val features: String,
    val epochs_run: Int
)

data class EToNewResponse(
    val success: Boolean,
    val eto_mm_per_day: Double,
    val unit: String,
    val model_used: EToModelUsed,
    val inputs_used: Map<String, Double>,
    val warnings: List<String>
)

// ── ETo API Interface ─────────────────────────────────────────────────
interface EToApiService {

    @GET("models")
    suspend fun getModels(): ModelResponse

    @POST("predict")
    suspend fun predictETo(@Body request: EToNewRequest): Response<EToNewResponse>
}

// ── Open-Meteo Historical Forecast ────────────────────────────────────
data class MeteoResponse(
    val daily: DailyData,
    val hourly: HourlyData
) {
    data class DailyData(
        val temperature_2m_max: List<Double>,
        val temperature_2m_min: List<Double>,
        val windspeed_10m_mean: List<Double>
    )
    data class HourlyData(
        val relativehumidity_2m: List<Int>,
        val shortwave_radiation: List<Double>,
        val windspeed_10m: List<Double>
    )
}

interface OpenMeteoHistoricalApi {
    @GET("v1/forecast")
    suspend fun getTodayData(
        @Query("latitude")        lat: Double,
        @Query("longitude")       lon: Double,
        @Query("daily")           daily: String = "temperature_2m_max,temperature_2m_min,windspeed_10m_mean",
        @Query("hourly")          hourly: String = "relativehumidity_2m,shortwave_radiation,windspeed_10m",
        @Query("past_days")       pastDays: Int = 0,
        @Query("forecast_days")   forecastDays: Int = 1,
        @Query("timezone")        timezone: String = "auto",
        @Query("wind_speed_unit") windUnit: String = "ms"
    ): Response<MeteoResponse>
}

// ── OpenWeatherMap Current Weather ────────────────────────────────────
data class OwmResponse(
    val wind: WindData
) {
    data class WindData(
        val speed: Double
    )
}

interface OpenWeatherMapApi {
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat")   lat: Double,
        @Query("lon")   lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): Response<OwmResponse>
}

// ── Retrofit Singletons ───────────────────────────────────────────────
object RetrofitClient {

    val etoApi: EToApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://akhilgarg29-eto-prediction-api.hf.space/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(EToApiService::class.java)
    }

    val meteoApi: OpenMeteoHistoricalApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.open-meteo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenMeteoHistoricalApi::class.java)
    }

    val owmApi: OpenWeatherMapApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenWeatherMapApi::class.java)
    }
}
