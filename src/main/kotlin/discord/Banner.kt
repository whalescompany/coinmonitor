package cc.makin.coinmonitor.discord

import cc.makin.coinmonitor.Banner
import cc.makin.coinmonitor.CoinResult
import cc.makin.coinmonitor.format
import cc.makin.coinmonitor.idofcow.IdOfCowStats
import cc.makin.coinmonitor.replayState
import dev.kord.common.entity.Snowflake
import dev.kord.common.entity.optional.optional
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.json.request.EmbedFieldRequest
import dev.kord.rest.json.request.EmbedRequest
import dev.kord.rest.json.request.EmbedThumbnailRequest
import dev.kord.rest.json.request.MessageCreateRequest
import dev.kord.rest.json.request.MessageEditPatchRequest
import dev.kord.rest.json.request.MultipartMessageCreateRequest
import dev.kord.rest.json.request.MultipartMessagePatchRequest
import dev.kord.rest.service.ChannelService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.sample
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

data class DiscordBannerEmbed(
    val title: String,
    val thumbnail: EmbedThumbnailRequest,
    val fields: List<EmbedFieldRequest>,
    val lastUpdated: Instant,
)

fun DiscordBannerEmbed.toMessageCreateRequest() =
    MultipartMessageCreateRequest(
        MessageCreateRequest(
            embeds = listOf(
                EmbedRequest(
                    title = title.optional(),
                    thumbnail = thumbnail.optional(),
                    fields = fields.optional(),
                    timestamp = lastUpdated.optional(),
                ),
            ).optional(),
        )
    )

fun DiscordBannerEmbed.toMessageModifyRequest() =
    MultipartMessagePatchRequest(
        MessageEditPatchRequest(
            embeds = listOf(
                EmbedRequest(
                    title = title.optional(),
                    thumbnail = thumbnail.optional(),
                    fields = fields.optional(),
                    timestamp = lastUpdated.optional(),
                ),
            ).optional(),
        )
    )

object DiscordBannerRenderer {
    @ExperimentalStdlibApi
    fun render(banner: Banner) = DiscordBannerEmbed(
        title = banner.name,
        thumbnail = EmbedBuilder.Thumbnail().apply {
            url = "https://cryptologos.cc/logos/loopring-lrc-logo.png"
        }.toRequest(),
        fields = buildList {
            addAll(banner.coins.toEmbedFields())
            banner.idOfCowStats?.let { add(it.toEmbedField()) }
        },
        lastUpdated = Clock.System.now(),
    )

    private fun IdOfCowStats.toEmbedField() = EmbedFieldRequest(
        name = "id of cow POLSKA",
        value = "DAILY: " + this.zakazeniaDzienne,
        inline = false.optional(),
    )

    private fun CoinResult.Ok.toEmbedField() = EmbedFieldRequest(
        name = name,
        value = this.price.format(),
        inline = true.optional(),
    )

    private fun CoinResult.Error.toEmbedField() = EmbedFieldRequest(
        name = name,
        value = "price fetch error",
        inline = true.optional(),
    )

    private fun CoinResult.toEmbedField() = when (this) {
        is CoinResult.Ok -> this.toEmbedField()
        is CoinResult.Error -> this.toEmbedField()
    }

    private fun List<CoinResult>.toEmbedFields() = this.map { it.toEmbedField() }
}

data class DiscordBanner(
    val messageId: Snowflake,
    val bannerEmbed: DiscordBannerEmbed,
    val createdAt: Instant,
)

@ExperimentalStdlibApi
@FlowPreview
@ExperimentalTime
@ExperimentalCoroutinesApi
fun liveDiscordBanner(
    coins: Iterable<StateFlow<CoinResult>>,
    idOfCowStats: StateFlow<IdOfCowStats>,
    name: String,
) = run {
    (coins + idOfCowStats)
        .merge()
        .sample(Duration.seconds(5))
        .replayState()
        .map { Banner(name = name, coins.map { it.value }, idOfCowStats = idOfCowStats.value) }
        .map { DiscordBannerRenderer.render(it) }
        .distinctUntilChanged()
        .conflate()
        .flowOn(Dispatchers.IO)
}

@ExperimentalStdlibApi
@FlowPreview
@ExperimentalTime
@ExperimentalCoroutinesApi
suspend fun Flow<DiscordBannerEmbed>.render(
    channelId: Snowflake,
    channelService: ChannelService,
    logger: Logger? = LoggerFactory.getLogger("LiveDiscordBannerRenderer"),
) {
    var lastBanner: DiscordBanner? = null

    this.collect { bannerEmbed ->
        withTimeoutOrNull(Duration.seconds(10)) {
            try {
                lastBanner = updateBanner(lastBanner, logger, bannerEmbed, channelService, channelId)
            } catch (th: Throwable) {
                logger?.error("Failed to update banner ${bannerEmbed.title}.", th)
            }
        }
    }
}

@ExperimentalTime
@ExperimentalCoroutinesApi
private suspend fun updateBanner(
    currentBanner: DiscordBanner?,
    logger: Logger?,
    bannerEmbed: DiscordBannerEmbed,
    channelService: ChannelService,
    channelId: Snowflake,
) = if (currentBanner != null && currentBanner.createdAt >= Clock.System.now().minus(Duration.minutes(30))) {
    channelService.editMessage(channelId, currentBanner.messageId, bannerEmbed.toMessageModifyRequest())

    currentBanner
} else {
    logger?.info("Creating new message of $bannerEmbed")
    val messageCreated = channelService.createMessage(channelId, bannerEmbed.toMessageCreateRequest())
    DiscordBanner(messageCreated.id, bannerEmbed, Clock.System.now())
}
