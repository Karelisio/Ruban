package com.karelisio.ruban.data.net

import com.karelisio.ruban.data.repo.PhotoStore
import com.karelisio.ruban.domain.LinkPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * Reads title, picture and price of a product page from Open Graph tags and JSON-LD `Product` data.
 * Returns null when the site refuses the request (bot protection, errors): the caller falls back to manual input.
 */
class LinkExtractor(private val http: OkHttpClient, private val photos: PhotoStore) {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    suspend fun fetch(url: String): LinkPreview? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "fr-FR,fr;q=0.9,en;q=0.6")
                .build()
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val type = response.header("Content-Type").orEmpty()
                if (type.isNotEmpty() && !type.contains("html")) return@runCatching null
                val body = response.body.source().let { src ->
                    src.request(MAX_BYTES)
                    src.buffer.clone().readUtf8()
                }
                val doc = Jsoup.parse(body, response.request.url.toString())
                if (looksBlocked(doc)) null else parse(doc)
            }
        }.getOrNull()
    }

    /** Only the price, for the daily price check. */
    suspend fun fetchPrice(url: String): Pair<Long, String?>? =
        fetch(url)?.let { p -> p.priceCents?.let { it to p.currency } }

    suspend fun downloadImage(url: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            http.newCall(request).execute().use { r ->
                if (!r.isSuccessful) return@runCatching null
                val length = r.body.contentLength()
                if (length > MAX_IMAGE_BYTES) return@runCatching null
                r.body.bytes()
            }
        }.getOrNull()?.let { photos.importBytes(it) }
    }

    private fun looksBlocked(doc: Document): Boolean {
        val title = doc.title().lowercase()
        return listOf("captcha", "robot", "access denied", "attention required", "just a moment", "accès refusé", "are you human")
            .any { title.contains(it) }
    }

    fun parse(doc: Document): LinkPreview {
        val product = doc.select("script[type=application/ld+json]")
            .asSequence()
            .mapNotNull { runCatching { json.parseToJsonElement(it.data()) }.getOrNull() }
            .flatMap { flatten(it) }
            .firstOrNull { isType(it, "Product") }

        fun meta(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
            doc.selectFirst("meta[property=$key], meta[name=$key], meta[itemprop=$key]")?.attr("content")?.takeIf { it.isNotBlank() }
        }

        val ldName = product?.str("name")
        val ldImage = product?.get("image")?.let(::firstImage)
        val offer = product?.get("offers")?.let(::firstOffer)
        val ldPrice = offer?.let { o ->
            o.str("price") ?: o.str("lowPrice") ?: (o["priceSpecification"]?.let(::firstObject))?.str("price")
        }
        val ldCurrency = offer?.let { o -> o.str("priceCurrency") ?: (o["priceSpecification"]?.let(::firstObject))?.str("priceCurrency") }

        val title = (meta("og:title", "twitter:title") ?: ldName ?: doc.title()).trim().takeIf { it.isNotBlank() }
        val image = (meta("og:image", "og:image:url", "og:image:secure_url", "twitter:image") ?: ldImage)
            ?.let { doc.selectFirst("meta[property=og:image]")?.absUrl("content")?.takeIf(String::isNotBlank) ?: absolutize(doc, it) }
        val priceRaw = meta("product:price:amount", "og:price:amount", "price") ?: ldPrice
            ?: doc.selectFirst("[itemprop=price]")?.let { it.attr("content").ifBlank { it.text() } }
        val currencyRaw = meta("product:price:currency", "og:price:currency", "priceCurrency") ?: ldCurrency
            ?: doc.selectFirst("[itemprop=priceCurrency]")?.let { it.attr("content").ifBlank { it.text() } }
        val canonical = doc.selectFirst("link[rel=canonical]")?.absUrl("href")?.takeIf { it.startsWith("http") }

        return LinkPreview(
            title = title?.let(::cleanTitle),
            imageUrl = image,
            priceCents = LinkPreview.parsePrice(priceRaw),
            currency = LinkPreview.normalizeCurrency(currencyRaw) ?: if (priceRaw?.contains('€') == true) "EUR" else null,
            siteName = meta("og:site_name"),
            canonicalUrl = canonical,
        )
    }

    private fun absolutize(doc: Document, url: String): String =
        if (url.startsWith("http")) url else runCatching { java.net.URI(doc.location()).resolve(url).toString() }.getOrDefault(url)

    private fun cleanTitle(t: String): String = t.replace(Regex("\\s+"), " ").take(160)

    private fun flatten(e: JsonElement): Sequence<JsonObject> = when (e) {
        is JsonArray -> e.asSequence().flatMap(::flatten)
        is JsonObject -> sequenceOf(e) + (e["@graph"]?.let(::flatten) ?: emptySequence())
        else -> emptySequence()
    }

    private fun isType(o: JsonObject, type: String): Boolean = when (val t = o["@type"]) {
        is JsonPrimitive -> t.contentOrNull.equals(type, true) || t.contentOrNull?.endsWith("/$type") == true
        is JsonArray -> t.any { (it as? JsonPrimitive)?.contentOrNull.equals(type, true) }
        else -> false
    }

    private fun JsonObject.str(key: String): String? = when (val v = this[key]) {
        is JsonPrimitive -> v.contentOrNull?.takeIf { it.isNotBlank() }
        else -> null
    }

    private fun firstObject(e: JsonElement): JsonObject? = when (e) {
        is JsonObject -> e
        is JsonArray -> e.firstOrNull { it is JsonObject } as? JsonObject
        else -> null
    }

    private fun firstOffer(e: JsonElement): JsonObject? {
        val o = firstObject(e) ?: return null
        // AggregateOffer may nest offers.
        return if (o.str("price") == null && o.str("lowPrice") == null && o["offers"] != null) o["offers"]?.let(::firstOffer) ?: o else o
    }

    private fun firstImage(e: JsonElement): String? = when (e) {
        is JsonPrimitive -> e.contentOrNull
        is JsonArray -> e.firstNotNullOfOrNull(::firstImage)
        is JsonObject -> e.str("url") ?: e.str("contentUrl")
    }

    companion object {
        const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 15; Pixel 9) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Mobile Safari/537.36"
        private const val MAX_BYTES = 3L * 1024 * 1024
        private const val MAX_IMAGE_BYTES = 10L * 1024 * 1024
    }
}
