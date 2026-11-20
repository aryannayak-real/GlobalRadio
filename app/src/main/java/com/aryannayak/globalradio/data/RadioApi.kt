package com.aryannayak.globalradio.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val favicon: String,
    val country: String,
    val countryCode: String,
    val language: String,
    val tags: String,
    val bitrate: Int,
    val codec: String
)

data class RadioCountry(
    val name: String,
    val code: String,
    val stationCount: Int
)

object RadioApi {
    private const val BASE =
        "https://all.api.radio-browser.info"

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun encode(value: String): String =
        URLEncoder.encode(value, "UTF-8")

    private fun JSONObject.str(key: String): String {
        return optString(key, "").takeUnless {
            it == "null"
        } ?: ""
    }

    private fun request(path: String): JSONArray {
        val req = Request.Builder()
            .url(BASE + path)
            .header("User-Agent", "GlobalRadio/1.0 Android")
            .build()

        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                error("Radio directory error: ${response.code}")
            }

            return JSONArray(response.body?.string() ?: "[]")
        }
    }

    private fun parse(array: JSONArray): List<RadioStation> {
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null

            val stream = item.str("url_resolved")
                .ifBlank { item.str("url") }

            if (stream.isBlank()) return@mapNotNull null

            RadioStation(
                id = item.str("stationuuid"),
                name = item.str("name").ifBlank { "Unknown station" },
                streamUrl = stream,
                favicon = item.str("favicon"),
                country = item.str("country"),
                countryCode = item.str("countrycode"),
                language = item.str("language"),
                tags = item.str("tags"),
                bitrate = item.optInt("bitrate"),
                codec = item.str("codec")
            )
        }
    }

    suspend fun top(): List<RadioStation> =
        withContext(Dispatchers.IO) {
            parse(request("/json/stations/topclick/40"))
        }

    suspend fun search(query: String): List<RadioStation> =
        withContext(Dispatchers.IO) {
            parse(
                request(
                    "/json/stations/search?name=${encode(query)}" +
                    "&limit=50&hidebroken=true&order=clickcount&reverse=true"
                )
            )
        }

    suspend fun byCountry(code: String): List<RadioStation> =
        withContext(Dispatchers.IO) {
            parse(
                request(
                    "/json/stations/bycountrycodeexact/${encode(code)}" +
                    "?limit=50&hidebroken=true&order=clickcount&reverse=true"
                )
            )
        }

    suspend fun byTag(tag: String): List<RadioStation> =
        withContext(Dispatchers.IO) {
            parse(
                request(
                    "/json/stations/bytag/${encode(tag)}" +
                    "?limit=50&hidebroken=true&order=clickcount&reverse=true"
                )
            )
        }

    suspend fun countries(): List<RadioCountry> =
        withContext(Dispatchers.IO) {
            val array = request(
                "/json/countries?order=stationcount&reverse=true"
            )

            (0 until array.length()).mapNotNull { i ->
                val item = array.optJSONObject(i)
                    ?: return@mapNotNull null

                RadioCountry(
                    name = item.str("name"),
                    code = item.str("iso_3166_1"),
                    stationCount = item.optInt("stationcount")
                )
            }.filter {
                it.name.isNotBlank() && it.code.isNotBlank()
            }
        }
}
