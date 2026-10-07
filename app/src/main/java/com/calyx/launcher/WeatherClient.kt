package com.calyx.launcher

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

 data class WeatherNow(val location: String, val temperatureC: Double, val description: String)

/** Manual-city weather lookup. The city name is sent to Open-Meteo; device location is never read. */
object WeatherClient {
    fun fetch(city: String): WeatherNow {
        require(city.trim().length >= 2) { "Enter a city name" }
        val query = URLEncoder.encode(city.trim(), "UTF-8")
        val geo = request("https://geocoding-api.open-meteo.com/v1/search?name=$query&count=1&language=en&format=json")
        val result = geo.optJSONArray("results")?.optJSONObject(0) ?: error("Location not found")
        val lat = result.getDouble("latitude")
        val lon = result.getDouble("longitude")
        val cityName = result.optString("name", city.trim())
        val country = result.optString("country", "")
        val forecast = request("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto")
        val current = forecast.getJSONObject("current")
        val code = current.optInt("weather_code", -1)
        return WeatherNow(listOf(cityName, country).filter(String::isNotBlank).joinToString(", "), current.getDouble("temperature_2m"), describe(code))
    }

    private fun request(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 7000
        connection.readTimeout = 7000
        connection.requestMethod = "GET"
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("User-Agent", "CalyxLauncher/0.3")
        try {
            if (connection.responseCode !in 200..299) error("Weather service returned ${connection.responseCode}")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { JSONObject(it.readText()) }
        } finally { connection.disconnect() }
    }

    private fun describe(code: Int): String = when (code) {
        0 -> "Clear sky"
        1, 2 -> "Mostly clear"
        3 -> "Overcast"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing drizzle"
        61, 63, 65 -> "Rain"
        66, 67 -> "Freezing rain"
        71, 73, 75, 77 -> "Snow"
        80, 81, 82 -> "Rain showers"
        85, 86 -> "Snow showers"
        95, 96, 99 -> "Thunderstorm"
        else -> "Current conditions"
    }
}
