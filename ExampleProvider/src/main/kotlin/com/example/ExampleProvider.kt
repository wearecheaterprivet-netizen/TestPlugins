package com.example

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class MovieLinkBDProvider : MainAPI() {
    override var mainUrl = "https://movielinkbd.tv"
    override var name = "MovieLinkBD"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama)
    override var lang = "bn"
    override val hasMainPage = true

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page <= 1) mainUrl else "$mainUrl/page/$page/"
        val document = app.get(url).document
        
        val items = document.select("article, .post-item, .item").mapNotNull { element ->
            val titleElement = element.selectFirst("h2, .entry-title, .title, a") ?: return@mapNotNull null
            val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = titleElement.text().trim()
            val posterUrl = element.selectFirst("img")?.attr("src") ?: element.selectFirst("img")?.attr("data-src")

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }

        return newHomePageResponse(listOf(HomePageList("Recently Updated", items)), hasNext = items.isNotEmpty())
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query"
        val document = app.get(searchUrl).document

        return document.select("article, .post-item, .item").mapNotNull { element ->
            val titleElement = element.selectFirst("h2, .entry-title, .title, a") ?: return@mapNotNull null
            val href = element.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val title = titleElement.text().trim()
            val posterUrl = element.selectFirst("img")?.attr("src") ?: element.selectFirst("img")?.attr("data-src")

            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1, .entry-title")?.text()?.trim() ?: "Unknown"
        val poster = document.selectFirst(".poster img, .entry-content img")?.attr("src")
        val plot = document.selectFirst(".entry-content p, .description")?.text()?.trim()

        val isSeries = url.contains("series", ignoreCase = true) || document.select(".episode, .season").isNotEmpty()

        return if (isSeries) {
            val episodes = mutableListOf<Episode>()
            document.select("a[href*='/episode/'], .episode-list a").forEachIndexed { index, epLink ->
                val epHref = epLink.attr("href")
                val epName = epLink.text().ifEmpty { "Episode ${index + 1}" }
                episodes.add(
                    newEpisode(epHref) {
                        this.name = epName
                        this.episode = index + 1
                    }
                )
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        offsetCallback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        document.select("iframe[src], a[href*='stream'], a[href*='download']").forEach { link ->
            val videoUrl = link.attr("src").ifEmpty { link.attr("href") }
            if (videoUrl.isNotBlank() && videoUrl.startsWith("http")) {
                loadExtractor(videoUrl, subtitleCallback, offsetCallback)
            }
        }
        return true
    }
}

