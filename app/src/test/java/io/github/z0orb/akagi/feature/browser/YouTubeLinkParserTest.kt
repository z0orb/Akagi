package io.github.z0orb.akagi.feature.browser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeLinkParserTest {

    private val id = "dQw4w9WgXcQ"

    private val videoLinks = listOf(
        "https://www.youtube.com/watch?v=$id",
        "https://youtube.com/watch?v=$id&t=42s",
        "https://youtu.be/$id",
        "https://youtu.be/$id?si=AbCdEfGh12345678&t=10",
        "youtu.be/$id",
        "www.youtube.com/watch?v=$id",
        "youtube.com/shorts/$id?feature=share",
        "https://www.youtube.com/shorts/$id",
        "https://youtube.com/shorts/$id/",
        "https://www.youtube.com/live/$id?si=xyz",
        "https://www.youtube.com/embed/$id?start=5",
        "https://www.youtube-nocookie.com/embed/$id",
        "https://www.youtube.com/v/$id",
        "https://www.youtube.com/watch?app=desktop&v=$id&feature=youtu.be",
        "HTTP://WWW.YOUTUBE.COM/watch?v=$id",
        "https://www.youtube.com/watch?v=$id#t=30",
        "https://www.youtube.com/watch?v=$id&pp=ygUJcmljayByb2xs|0",
        "https://www.youtube.com/attribution_link?a=x&u=%2Fwatch%3Fv%3D$id%26feature%3Dshare",
        "Check this out https://youtu.be/$id?si=AbCdEfGh12345678",
        "Rick Astley - Never Gonna Give You Up (Official Video)\nhttps://youtu.be/$id",
        "(https://youtu.be/$id).",
        "  https://youtu.be/$id  ",
        "Watch:https://m.youtube.com/watch?v=$id",
    )

    @Test
    fun `every video link shape yields the bare video ID`() {
        videoLinks.forEach { link ->
            assertEquals(link, LinkTarget.Video(id), YouTubeLinkParser.parse(link))
        }
    }

    @Test
    fun `a music link opens on YouTube Music`() {
        assertEquals(
            LinkTarget.Video(id, Site.YOUTUBE_MUSIC),
            YouTubeLinkParser.parse("https://music.youtube.com/watch?v=$id&si=AbCdEfGh12345678"),
        )
        assertEquals(
            "https://music.youtube.com/watch?v=$id",
            YouTubeLinkParser.parse("music.youtube.com/watch?v=$id")?.url,
        )
    }

    @Test
    fun `a watch link keeps its playlist and nothing else`() {
        val list = "PLrAXtmErZgOeiKm4sgNOknGvNjby9efdf"
        val target = YouTubeLinkParser.parse("https://m.youtube.com/watch?v=$id&list=$list&index=3&t=9")
        assertEquals(LinkTarget.Video(id, Site.YOUTUBE, list), target)
        assertEquals("https://m.youtube.com/watch?v=$id&list=$list", target?.url)
        assertEquals(
            LinkTarget.Video(id, Site.YOUTUBE_MUSIC, "RDAMVM$id"),
            YouTubeLinkParser.parse("https://music.youtube.com/watch?v=$id&list=RDAMVM$id"),
        )
    }

    @Test
    fun `a malformed list is dropped`() {
        assertEquals(LinkTarget.Video(id), YouTubeLinkParser.parse("https://youtu.be/$id?list=a"))
        assertEquals(LinkTarget.Video(id), YouTubeLinkParser.parse("https://youtu.be/$id?list=bad%20list"))
    }

    @Test
    fun `plain text searches the site on screen`() {
        assertEquals(
            LinkTarget.Search("teh hijau", Site.YOUTUBE_MUSIC),
            YouTubeLinkParser.parse("teh hijau", Site.YOUTUBE_MUSIC),
        )
        assertEquals(
            "https://music.youtube.com/search?q=teh+hijau",
            YouTubeLinkParser.parse("teh hijau", Site.YOUTUBE_MUSIC)?.url,
        )
    }

    @Test
    fun `a pasted link ignores the site on screen`() {
        assertEquals(
            LinkTarget.Video(id, Site.YOUTUBE),
            YouTubeLinkParser.parse("https://youtu.be/$id", Site.YOUTUBE_MUSIC),
        )
    }

    @Test
    fun `siteOf follows the host`() {
        assertEquals(Site.YOUTUBE_MUSIC, YouTubeLinkParser.siteOf("https://music.youtube.com/browse/MPREb_x"))
        assertEquals(Site.YOUTUBE, YouTubeLinkParser.siteOf("https://m.youtube.com/watch?v=$id"))
        assertEquals(Site.YOUTUBE, YouTubeLinkParser.siteOf("https://www.youtube.com/"))
        assertNull(YouTubeLinkParser.siteOf("https://accounts.google.com/ServiceLogin"))
        assertNull(YouTubeLinkParser.siteOf("about:blank"))
    }

    @Test
    fun `a music video page is a video page`() {
        assertEquals(
            VideoRef(id, isShort = false),
            YouTubeLinkParser.videoPageOf("https://music.youtube.com/watch?v=$id&list=RDAMVM$id"),
        )
    }

    @Test
    fun `IDs with dashes and underscores survive`() {
        assertEquals(
            LinkTarget.Video("a-b_c1D2e3F"),
            YouTubeLinkParser.parse("https://m.youtube.com/watch?feature=share&v=a-b_c1D2e3F"),
        )
    }

    @Test
    fun `video target opens the clean mobile watch URL`() {
        assertEquals("https://m.youtube.com/watch?v=$id", YouTubeLinkParser.parse("https://youtu.be/$id?t=5")?.url)
    }

    @Test
    fun `plain text becomes a search`() {
        assertEquals(
            LinkTarget.Search("never gonna give you up"),
            YouTubeLinkParser.parse("  never gonna give you up "),
        )
    }

    @Test
    fun `a bare ID is treated as a search`() {
        assertEquals(LinkTarget.Search(id), YouTubeLinkParser.parse(id))
    }

    @Test
    fun `look-alike hosts are not YouTube`() {
        listOf(
            "https://notyoutube.com/watch?v=$id",
            "https://example.com/youtube.com/watch?v=$id",
            "https://youtube.com.evil.com/watch?v=$id",
        ).forEach { link ->
            assertEquals(link, LinkTarget.Search(link), YouTubeLinkParser.parse(link))
        }
    }

    @Test
    fun `non-video YouTube links open the page on the mobile site`() {
        assertEquals(
            LinkTarget.Page("https://m.youtube.com/@RickAstleyYT"),
            YouTubeLinkParser.parse("https://www.youtube.com/@RickAstleyYT"),
        )
        assertEquals(
            LinkTarget.Page("https://m.youtube.com/playlist?list=PL123"),
            YouTubeLinkParser.parse("youtube.com/playlist?list=PL123"),
        )
        assertEquals(LinkTarget.Page(YouTubeUrls.HOME), YouTubeLinkParser.parse("https://youtu.be/"))
    }

    @Test
    fun `an invalid ID falls back to opening the page`() {
        assertEquals(
            LinkTarget.Page("https://m.youtube.com/watch?v=short"),
            YouTubeLinkParser.parse("https://www.youtube.com/watch?v=short"),
        )
    }

    @Test
    fun `blank input yields nothing`() {
        assertNull(YouTubeLinkParser.parse("   "))
    }

    @Test
    fun `search URL is form-encoded`() {
        assertEquals(
            "https://m.youtube.com/results?search_query=lofi+beats+%26+chill",
            LinkTarget.Search("lofi beats & chill").url,
        )
    }

    @Test
    fun `watch and Shorts pages are video pages`() {
        assertEquals(
            VideoRef(id, isShort = false),
            YouTubeLinkParser.videoPageOf("https://m.youtube.com/watch?v=$id&pp=x"),
        )
        assertEquals(VideoRef(id, isShort = true), YouTubeLinkParser.videoPageOf("https://m.youtube.com/shorts/$id"))
    }

    @Test
    fun `other pages are not video pages`() {
        listOf(
            "https://m.youtube.com/",
            "https://m.youtube.com/results?search_query=x",
            "https://m.youtube.com/watch",
            "https://m.youtube.com/embed/$id",
            "https://youtu.be/$id",
            "https://accounts.google.com/ServiceLogin",
            "about:blank",
        ).forEach { url -> assertNull(url, YouTubeLinkParser.videoPageOf(url)) }
    }
}
