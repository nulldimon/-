package com.example.control

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

object WeatherFetcher {

    private val client = OkHttpClient.Builder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(6, TimeUnit.SECONDS)
        .build()

    suspend fun fetchWeatherSummary(
        city: String,
        tomorrow: Boolean = false,
        askPrecipitation: Boolean = false
    ): Result<String> = withContext(Dispatchers.IO) {
        val cleanCity = normalizeRussianCityQuery(city.trim().ifBlank { "Москва" })

        val openMeteoResult = fetchFromOpenMeteo(cleanCity, tomorrow, askPrecipitation)
        if (openMeteoResult.isSuccess) return@withContext openMeteoResult

        val wttrResult = fetchFromWttr(cleanCity, tomorrow, askPrecipitation)
        if (wttrResult.isSuccess) return@withContext wttrResult

        openMeteoResult
    }

    private fun fetchFromOpenMeteo(
        cleanCity: String,
        tomorrow: Boolean,
        askPrecipitation: Boolean
    ): Result<String> {
        return try {
            val encodedCity = URLEncoder.encode(cleanCity, "UTF-8")
            val geoUrl = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=ru&format=json"
            val geoBody = executeGet(geoUrl)
                ?: return Result.failure(IllegalStateException("Нет ответа от службы погоды"))

            val geoJson = JSONObject(geoBody)
            val results = geoJson.optJSONArray("results")
            if (results == null || results.length() == 0) {
                return Result.failure(IllegalStateException("Город «$cleanCity» не найден"))
            }

            val place = results.getJSONObject(0)
            val lat = place.getDouble("latitude")
            val lon = place.getDouble("longitude")
            val resolvedName = place.optString("name", cleanCity)

            val weatherUrl = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
                "&forecast_days=2&wind_speed_unit=ms&timezone=auto"
            val weatherBody = executeGet(weatherUrl)
                ?: return Result.failure(IllegalStateException("Не удалось получить прогноз"))

            val weatherJson = JSONObject(weatherBody)
            val current = weatherJson.optJSONObject("current")
            val daily = weatherJson.optJSONObject("daily")

            if (tomorrow && daily != null) {
                val maxArr = daily.optJSONArray("temperature_2m_max")
                val minArr = daily.optJSONArray("temperature_2m_min")
                val codeArr = daily.optJSONArray("weather_code")
                val rainArr = daily.optJSONArray("precipitation_probability_max")
                if (maxArr != null && minArr != null && maxArr.length() >= 2) {
                    val tMax = maxArr.getDouble(1).roundToInt()
                    val tMin = minArr.getDouble(1).roundToInt()
                    val cond = describeWmoCode(codeArr?.optInt(1, 0) ?: 0)
                    val rainProb = rainArr?.optInt(1, 0) ?: 0
                    val msg = "Завтра в городе $resolvedName $cond, днём ${formatTemp(tMax)}, ночью ${formatTemp(tMin)}, вероятность осадков $rainProb процентов"
                    return Result.success(msg)
                }
            }

            if (askPrecipitation && daily != null) {
                val rainArr = daily.optJSONArray("precipitation_probability_max")
                val codeArr = daily.optJSONArray("weather_code")
                val rainToday = rainArr?.optInt(0, 0) ?: 0
                val cond = describeWmoCode(codeArr?.optInt(0, 0) ?: 0)
                val msg = if (rainToday >= 50) {
                    "Сегодня в городе $resolvedName вероятны осадки ($rainToday процентов), $cond"
                } else {
                    "Сегодня в городе $resolvedName вероятность осадков небольшая — $rainToday процентов, $cond"
                }
                return Result.success(msg)
            }

            if (current == null) {
                return Result.failure(IllegalStateException("Данные погоды недоступны"))
            }

            val temp = current.optDouble("temperature_2m", 0.0).roundToInt()
            val feelsLike = current.optDouble("apparent_temperature", temp.toDouble()).roundToInt()
            val windMs = current.optDouble("wind_speed_10m", 0.0).roundToInt()
            val code = current.optInt("weather_code", 0)
            val condition = describeWmoCode(code)

            val summary = "В городе $resolvedName сейчас ${formatTemp(temp)} градусов, $condition, ощущается как ${formatTemp(feelsLike)}, ветер $windMs метров в секунду"
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchFromWttr(
        cleanCity: String,
        tomorrow: Boolean,
        askPrecipitation: Boolean
    ): Result<String> {
        return try {
            val encoded = URLEncoder.encode(cleanCity, "UTF-8")
            val body = executeGet("https://wttr.in/$encoded?format=j1&lang=ru")
                ?: return Result.failure(IllegalStateException("Резервная служба погоды недоступна"))
            val root = JSONObject(body)
            val currentArr = root.optJSONArray("current_condition")
            val weatherArr = root.optJSONArray("weather")

            if (tomorrow && weatherArr != null && weatherArr.length() >= 2) {
                val day = weatherArr.getJSONObject(1)
                val tMax = day.optString("maxtempC", "0").toIntOrNull() ?: 0
                val tMin = day.optString("mintempC", "0").toIntOrNull() ?: 0
                return Result.success(
                    "Завтра в городе $cleanCity днём ${formatTemp(tMax)}, ночью ${formatTemp(tMin)}"
                )
            }

            if (currentArr == null || currentArr.length() == 0) {
                return Result.failure(IllegalStateException("Нет данных погоды"))
            }

            val cur = currentArr.getJSONObject(0)
            val temp = cur.optString("temp_C", "0").toIntOrNull() ?: 0
            val feels = cur.optString("FeelsLikeC", "$temp").toIntOrNull() ?: temp
            val windKmh = cur.optString("windspeedKmph", "0").toIntOrNull() ?: 0
            val windMs = (windKmh / 3.6).roundToInt()
            val descRu = cur.optJSONArray("lang_ru")?.optJSONObject(0)?.optString("value").orEmpty()
            val cond = descRu.ifBlank {
                cur.optJSONArray("weatherDesc")?.optJSONObject(0)?.optString("value").orEmpty()
            }

            if (askPrecipitation) {
                val precipMm = cur.optString("precipMM", "0").toDoubleOrNull() ?: 0.0
                val rainNote = if (precipMm > 0.1 || cond.contains("дожд", true) || cond.contains("снег", true)) {
                    "Ожидаются осадки"
                } else {
                    "Существенных осадков не ожидается"
                }
                val condPart = if (cond.isNotBlank()) ", $cond" else ""
                return Result.success("Сегодня в городе $cleanCity: $rainNote$condPart")
            }

            val condPart = if (cond.isNotBlank()) ", ${cond.lowercase()}" else ""
            Result.success(
                "В городе $cleanCity сейчас ${formatTemp(temp)} градусов$condPart, ощущается как ${formatTemp(feels)}, ветер $windMs метров в секунду"
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchCbrCurrencyRates(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = executeGet("https://www.cbr-xml-daily.ru/daily_json.js")
                ?: return@withContext Result.failure(IllegalStateException("Нет ответа от ЦБ РФ"))
            val root = JSONObject(body)
            val valute = root.optJSONObject("Valute")
                ?: return@withContext Result.failure(IllegalStateException("Курсы валют не найдены"))
            val usd = valute.optJSONObject("USD")?.optDouble("Value", 0.0) ?: 0.0
            val eur = valute.optJSONObject("EUR")?.optDouble("Value", 0.0) ?: 0.0
            if (usd <= 0.0 || eur <= 0.0) {
                return@withContext Result.failure(IllegalStateException("Данные о курсах отсутствуют"))
            }
            val usdRub = usd.roundToInt()
            val eurRub = eur.roundToInt()
            Result.success("По курсу Центробанка доллар около $usdRub рублей, евро около $eurRub рублей")
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun normalizeRussianCityQuery(city: String): String {
        val lower = city.lowercase()
        val exactMap = mapOf(
            "москве" to "Москва",
            "питере" to "Санкт-Петербург",
            "санкт петербурге" to "Санкт-Петербург",
            "петербурге" to "Санкт-Петербург",
            "казани" to "Казань",
            "екатеринбурге" to "Екатеринбург",
            "новосибирске" to "Новосибирск",
            "краснодаре" to "Краснодар",
            "сочи" to "Сочи",
            "уфе" to "Уфа",
            "самаре" to "Самара",
            "ростове" to "Ростов-на-Дону",
            "воронеже" to "Воронеж",
            "нижнем новгороде" to "Нижний Новгород",
            "челябинске" to "Челябинск",
            "омске" to "Омск",
            "перми" to "Пермь",
            "волгограде" to "Волгоград",
            "красноярске" to "Красноярск",
            "минске" to "Минск",
            "алматы" to "Алматы",
            "астане" to "Астана"
        )
        return exactMap[lower] ?: city
    }

    private fun formatTemp(t: Int): String = when {
        t > 0 -> "плюс $t"
        t < 0 -> "минус ${-t}"
        else -> "ноль"
    }

    private fun executeGet(url: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "GolosGidAndroid/1.0")
            .build()
        client.newCall(request).execute().use { resp ->
            if (!resp.isSuccessful) return null
            return resp.body?.string()
        }
    }

    private fun describeWmoCode(code: Int): String = when (code) {
        0 -> "ясно"
        1 -> "преимущественно ясно"
        2 -> "переменная облачность"
        3 -> "пасмурно"
        45, 48 -> "туман"
        51, 53, 55 -> "морось"
        56, 57 -> "ледяная морось"
        61, 63, 65 -> "дождь"
        66, 67 -> "ледяной дождь"
        71, 73, 75, 77 -> "снег"
        80, 81, 82 -> "ливень"
        85, 86 -> "снегопад"
        95, 96, 99 -> "гроза"
        else -> "облачно"
    }
}
