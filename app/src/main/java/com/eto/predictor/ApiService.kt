package com.eto.predictor

import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Query

// ── ETo Prediction Models ─────────────────────────────────────────────
data class EToRequest(
    val n: Double?,
    val tmax: Double?,
    val tmin: Double?,
    val rhmax: Double?,
    val rhmin: Double?,
    val u: Double?
)

data class EToResponse(
    val eto: Double,
    val unit: String,
    val params_provided: Int,
    val imputed_values: Map<String, Double>
)

interface EToApiService {
    @POST("predict")
    suspend fun predictETo(@Body request: EToRequest): Response<EToResponse>
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
        val direct_radiation: List<Double>,   // used for sunshine hours
        val windspeed_10m: List<Double>        // used for FAO-56 wind conversion
    )
}

interface OpenMeteoHistoricalApi {
    @GET("v1/forecast")
    suspend fun getTodayData(
        @Query("latitude") lat: Double,
        @Query("longitude") lon: Double,
        @Query("daily") daily: String = "temperature_2m_max,temperature_2m_min,windspeed_10m_mean",
        @Query("hourly") hourly: String = "relativehumidity_2m,direct_radiation,windspeed_10m",
        @Query("past_days") pastDays: Int = 0,
        @Query("forecast_days") forecastDays: Int = 1,
        @Query("timezone") timezone: String = "auto",
        @Query("wind_speed_unit") windUnit: String = "ms"
    ): Response<MeteoResponse>
}

// ── Retrofit Singletons ───────────────────────────────────────────────
object RetrofitClient {

    val etoApi: EToApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://eto-predictor-1.onrender.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(EToApiService::class.java)
    }

    val meteoApi: OpenMeteoHistoricalApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://historical-forecast-api.open-meteo.com/")
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
// ── OpenWeatherMap Current Weather — real station wind data ───────────
data class OwmResponse(
    val wind: WindData
) {
    data class WindData(
        val speed: Double  // m/s at 10m
    )
}

interface OpenWeatherMapApi {
    @GET("data/2.5/weather")
    suspend fun getCurrentWeather(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("appid") apiKey: String,
        @Query("units") units: String = "metric"
    ): Response<OwmResponse>
}
