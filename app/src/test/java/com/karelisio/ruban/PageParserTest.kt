package com.karelisio.ruban

import com.karelisio.ruban.data.net.PageParser
import com.karelisio.ruban.domain.LinkPreview
import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageParserTest {
    @Test fun openGraph() {
        val html = """
            <html><head><title>Fallback</title>
            <meta property="og:title" content="Carnet en lin" />
            <meta property="og:image" content="/img/carnet.jpg" />
            <meta property="product:price:amount" content="24.90" />
            <meta property="product:price:currency" content="EUR" />
            </head><body></body></html>
        """.trimIndent()
        val p = PageParser.parse(Jsoup.parse(html, "https://boutique.example/carnet"))
        assertEquals("Carnet en lin", p.title)
        assertEquals("https://boutique.example/img/carnet.jpg", p.imageUrl)
        assertEquals(2490L, p.priceCents)
        assertEquals("EUR", p.currency)
    }

    @Test fun jsonLdProductInGraph() {
        val html = """
            <html><head><title>Shop</title>
            <script type="application/ld+json">
            {"@context":"https://schema.org","@graph":[{"@type":"WebPage"},
             {"@type":["Product"],"name":"Théière fonte","image":["https://cdn.example/t.jpg"],
              "offers":{"@type":"AggregateOffer","offers":[{"@type":"Offer","price":"1299,00","priceCurrency":"EUR"}]}}]}
            </script></head></html>
        """.trimIndent()
        val p = PageParser.parse(Jsoup.parse(html, "https://shop.example/p"))
        assertEquals("https://cdn.example/t.jpg", p.imageUrl)
        assertEquals(129900L, p.priceCents)
        assertEquals("EUR", p.currency)
    }

    @Test fun blocked() {
        assertTrue(PageParser.looksBlocked(Jsoup.parse("<title>Just a moment...</title>")))
    }

    @Test fun prices() {
        assertEquals(129999L, LinkPreview.parsePrice("1 299,99 €"))
        assertEquals(129900L, LinkPreview.parsePrice("1,299.00"))
        assertEquals(2990L, LinkPreview.parsePrice("29.9"))
        assertEquals(4500L, LinkPreview.parsePrice("45"))
        assertEquals("https://a.fr/x?y=1", LinkPreview.findUrl("Regarde ça https://a.fr/x?y=1, top !"))
    }
}
