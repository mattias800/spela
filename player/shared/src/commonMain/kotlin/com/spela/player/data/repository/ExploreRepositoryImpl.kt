package com.spela.player.data.repository

import com.spela.player.data.remote.api.SpelaApiClient
import com.spela.player.data.remote.dto.DeveloperDetailResponseDto
import com.spela.player.data.remote.dto.GameDto
import com.spela.player.data.remote.dto.toDomain
import com.spela.player.domain.model.AchievementGameItem
import com.spela.player.domain.model.ActiveNowItem
import com.spela.player.domain.model.AlmostDoneGame
import com.spela.player.domain.model.AnniversaryItem
import com.spela.player.domain.model.ArtworkItem
import com.spela.player.domain.model.CommunityTopGame
import com.spela.player.domain.model.ConsoleHighlight
import com.spela.player.domain.model.ConsoleShowcase
import com.spela.player.domain.model.CultClassicGame
import com.spela.player.domain.model.DeveloperDetail
import com.spela.player.domain.model.DeveloperSpotlight
import com.spela.player.domain.model.DeveloperSummary
import com.spela.player.domain.model.ExploreChallenge
import com.spela.player.domain.model.ExploreRow
import com.spela.player.domain.model.FeaturedGame
import com.spela.player.domain.model.FeaturedSeries

import com.spela.player.domain.model.ForYouRow
import com.spela.player.domain.model.FreshChallengeGame
import com.spela.player.domain.model.Game
import com.spela.player.domain.model.GameFranchiseLink
import com.spela.player.domain.model.GameSeriesLink
import com.spela.player.domain.model.Keyword
import com.spela.player.domain.model.SeriesDetail
import com.spela.player.domain.model.MoodDefinition
import com.spela.player.domain.model.PlayersLikeYouResult
import com.spela.player.domain.model.RecentReviewItem
import com.spela.player.domain.model.CompletionistMap
import com.spela.player.domain.model.ExplorerBadge
import com.spela.player.domain.model.SavedSearch
import com.spela.player.domain.model.ScreenshotItem
import com.spela.player.domain.model.TasteProfile
import com.spela.player.domain.model.Theme
import com.spela.player.domain.model.TrendingGame
import com.spela.player.domain.model.WizardResults
import com.spela.player.domain.model.WizardStep
import com.spela.player.domain.repository.ExploreRepository

class ExploreRepositoryImpl(
    private val apiClient: SpelaApiClient,
) : ExploreRepository {

    override suspend fun getFeaturedGames(): Result<List<FeaturedGame>> = runCatching {
        apiClient.getExploreFeatured().map { dto ->
            dto.toDomain().copy(
                heroUrl = apiClient.resolveUrl(dto.heroUrl),
                logoUrl = apiClient.resolveUrl(dto.logoUrl),
            )
        }
    }

    override suspend fun getExploreRows(): Result<List<ExploreRow>> = runCatching {
        apiClient.getExploreRows().map { dto ->
            dto.toDomain().copy(
                games = dto.games.orEmpty().map { gameDto ->
                    gameDto.toDomain().copy(
                        coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
                        heroUrl = apiClient.resolveUrl(gameDto.heroUrl),
                        logoUrl = apiClient.resolveUrl(gameDto.logoUrl),
                    )
                },
            )
        }
    }

    override suspend fun getThemes(): Result<List<Theme>> = runCatching {
        apiClient.getThemes().map { it.toDomain() }
    }

    override suspend fun getThemeGames(themeId: String, page: Int, pageSize: Int): Result<List<Game>> = runCatching {
        apiClient.getThemeGames(themeId, page, pageSize).data.orEmpty().map { gameDto ->
            gameDto.toDomain().copy(
                coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
                heroUrl = apiClient.resolveUrl(gameDto.heroUrl),
                logoUrl = apiClient.resolveUrl(gameDto.logoUrl),
            )
        }
    }

    override suspend fun getKeywords(limit: Int): Result<List<Keyword>> = runCatching {
        apiClient.getKeywords(limit).map { it.toDomain() }
    }

    override suspend fun getKeywordGames(keywordId: String, page: Int, pageSize: Int): Result<List<Game>> = runCatching {
        apiClient.getKeywordGames(keywordId, page, pageSize).data.orEmpty().map { gameDto ->
            gameDto.toDomain().copy(
                coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
                heroUrl = apiClient.resolveUrl(gameDto.heroUrl),
                logoUrl = apiClient.resolveUrl(gameDto.logoUrl),
            )
        }
    }

    override suspend fun getFeaturedSeries(): Result<List<FeaturedSeries>> = runCatching {
        apiClient.getFeaturedSeries().map { dto ->
            dto.toDomain().copy(
                heroUrl = apiClient.resolveUrl(dto.heroUrl),
            )
        }
    }

    override suspend fun getSeriesDetail(id: String): Result<SeriesDetail> = runCatching {
        val dto = apiClient.getSeriesDetail(id)
        dto.toDomain().copy(
            heroUrl = apiClient.resolveUrl(dto.heroUrl),
            logoUrl = apiClient.resolveUrl(dto.logoUrl),
            games = dto.games.orEmpty().map { gameDto ->
                gameDto.toDomain().copy(
                    coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
                )
            },
        )
    }

    override suspend fun getFranchiseDetail(id: String): Result<SeriesDetail> = runCatching {
        val dto = apiClient.getFranchiseDetail(id)
        dto.toDomain().copy(
            heroUrl = apiClient.resolveUrl(dto.heroUrl),
            logoUrl = apiClient.resolveUrl(dto.logoUrl),
            games = dto.games.orEmpty().map { gameDto ->
                gameDto.toDomain().copy(
                    coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
                )
            },
        )
    }

    override suspend fun getGameSeries(gameId: String): Result<List<GameSeriesLink>> = runCatching {
        apiClient.getGameSeries(gameId).map { it.toDomain() }
    }

    override suspend fun getGameFranchises(gameId: String): Result<List<GameFranchiseLink>> = runCatching {
        apiClient.getGameFranchises(gameId).map { it.toDomain() }
    }


    override suspend fun getMoods(): Result<List<MoodDefinition>> = runCatching {
        apiClient.getMoods().map { it.toDomain() }
    }

    override suspend fun getMoodGames(mood: String): Result<List<Game>> = runCatching {
        apiClient.getMoodGames(mood).map { gameDto ->
            gameDto.toDomain().copy(
                coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
                heroUrl = apiClient.resolveUrl(gameDto.heroUrl),
                logoUrl = apiClient.resolveUrl(gameDto.logoUrl),
            )
        }
    }

    override suspend fun getSurpriseGame(): Result<Game> = runCatching {
        val gameDto = apiClient.getSurpriseGame()
        gameDto.toDomain().copy(
            coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
            heroUrl = apiClient.resolveUrl(gameDto.heroUrl),
            logoUrl = apiClient.resolveUrl(gameDto.logoUrl),
        )
    }

    override suspend fun getForYou(): Result<List<ForYouRow>> = runCatching {
        apiClient.getForYou().rows.map { rowDto ->
            rowDto.toDomain().copy(
                sourceGame = rowDto.sourceGame?.let { sgDto ->
                    sgDto.toDomain().copy(
                        coverUrl = apiClient.resolveUrl(sgDto.coverUrl),
                        heroUrl = apiClient.resolveUrl(sgDto.heroUrl),
                        logoUrl = apiClient.resolveUrl(sgDto.logoUrl),
                    )
                },
                games = rowDto.games.map { gameDto ->
                    gameDto.toDomain().copy(
                        coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
                        heroUrl = apiClient.resolveUrl(gameDto.heroUrl),
                        logoUrl = apiClient.resolveUrl(gameDto.logoUrl),
                    )
                },
            )
        }
    }

    override suspend fun getTasteProfile(): Result<TasteProfile> = runCatching {
        apiClient.getTasteProfile().toDomain()
    }

    override suspend fun getPlayersLikeYou(): Result<PlayersLikeYouResult> = runCatching {
        val dto = apiClient.getPlayersLikeYou()
        dto.toDomain().copy(
            games = dto.games.map { gameDto ->
                gameDto.toDomain().copy(
                    coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
                    heroUrl = apiClient.resolveUrl(gameDto.heroUrl),
                    logoUrl = apiClient.resolveUrl(gameDto.logoUrl),
                )
            },
        )
    }

    override suspend fun getDevelopers(): Result<List<DeveloperSummary>> = runCatching {
        apiClient.getDevelopers().developers.map { it.toDomain() }
    }

    override suspend fun getDeveloperDetail(name: String): Result<DeveloperDetail> = runCatching {
        resolveEntityDetail(apiClient.getDeveloperDetail(name))
    }

    override suspend fun getPublisherDetail(name: String): Result<DeveloperDetail> = runCatching {
        resolveEntityDetail(apiClient.getPublisherDetail(name))
    }

    override suspend fun getDeveloperSpotlight(): Result<DeveloperSpotlight> = runCatching {
        val dto = apiClient.getDeveloperSpotlight()
        dto.toDomain().copy(
            heroUrl = apiClient.resolveUrl(dto.heroUrl),
            topGames = dto.topGames.map { gameDto ->
                gameDto.toDomain().copy(
                    coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
                )
            },
        )
    }

    override suspend fun getConsoleShowcase(consoleId: String): Result<ConsoleShowcase> = runCatching {
        val dto = apiClient.getConsoleShowcase(consoleId)
        dto.toDomain().copy(
            essentials = dto.essentials.map { gameDto ->
                gameDto.toDomain().copy(
                    coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
                    heroUrl = apiClient.resolveUrl(gameDto.heroUrl),
                    logoUrl = apiClient.resolveUrl(gameDto.logoUrl),
                )
            },
            hiddenGems = dto.hiddenGems.map { gameDto ->
                gameDto.toDomain().copy(
                    coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
                    heroUrl = apiClient.resolveUrl(gameDto.heroUrl),
                    logoUrl = apiClient.resolveUrl(gameDto.logoUrl),
                )
            },
            recentlyPlayed = dto.recentlyPlayed.map { gameDto ->
                gameDto.toDomain().copy(
                    coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
                    heroUrl = apiClient.resolveUrl(gameDto.heroUrl),
                    logoUrl = apiClient.resolveUrl(gameDto.logoUrl),
                )
            },
        )
    }

    override suspend fun getConsoleHighlights(): Result<List<ConsoleHighlight>> = runCatching {
        apiClient.getConsoleHighlights().consoles.map { dto ->
            dto.toDomain().copy(
                iconUrl = apiClient.resolveUrl(dto.iconUrl) ?: "",
                logoUrl = apiClient.resolveUrl(dto.logoUrl) ?: "",
                topGame = dto.topGame?.let { gameDto ->
                    gameDto.toDomain().copy(
                        coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
                    )
                },
            )
        }
    }

    override suspend fun getArtworkGallery(page: Int): Result<List<ArtworkItem>> = runCatching {
        apiClient.getArtworkGallery(page).artworks.map { dto ->
            dto.toDomain().copy(
                url = apiClient.resolveUrl(dto.url) ?: dto.url,
            )
        }
    }

    override suspend fun getScreenshotGallery(page: Int): Result<List<ScreenshotItem>> = runCatching {
        apiClient.getScreenshotGallery(page).screenshots.map { dto ->
            dto.toDomain().copy(
                url = apiClient.resolveUrl(dto.url) ?: dto.url,
            )
        }
    }

    override suspend fun getTrending(): Result<List<TrendingGame>> = runCatching {
        apiClient.getTrending().games.map { dto ->
            dto.toDomain().copy(
                game = resolveGameCovers(dto.game.toDomain(), dto.game),
            )
        }
    }

    override suspend fun getCommunityTop(): Result<List<CommunityTopGame>> = runCatching {
        apiClient.getCommunityTop().games.map { dto ->
            dto.toDomain().copy(
                game = resolveGameCovers(dto.game.toDomain(), dto.game),
            )
        }
    }

    override suspend fun getCultClassics(): Result<List<CultClassicGame>> = runCatching {
        apiClient.getCultClassics().games.map { dto ->
            dto.toDomain().copy(
                game = resolveGameCovers(dto.game.toDomain(), dto.game),
            )
        }
    }

    override suspend fun getRecentlyReviewed(): Result<List<RecentReviewItem>> = runCatching {
        apiClient.getRecentlyReviewed().reviews.map { dto ->
            dto.toDomain().copy(
                game = resolveGameCovers(dto.game.toDomain(), dto.game),
            )
        }
    }

    override suspend fun getActiveNow(): Result<List<ActiveNowItem>> = runCatching {
        apiClient.getActiveNow().games.map { dto ->
            dto.toDomain().copy(
                game = resolveGameCovers(dto.game.toDomain(), dto.game),
            )
        }
    }

    override suspend fun getOnThisDay(): Result<Pair<String, List<Game>>> = runCatching {
        val dto = apiClient.getOnThisDay()
        val games = dto.games.map { gameDto ->
            resolveGameCovers(gameDto.toDomain(), gameDto)
        }
        Pair(dto.date, games)
    }

    override suspend fun getBestOfYear(year: Int): Result<List<Game>> = runCatching {
        apiClient.getBestOfYear(year).games.map { gameDto ->
            resolveGameCovers(gameDto.toDomain(), gameDto)
        }
    }

    override suspend fun getYourAnniversaries(): Result<List<AnniversaryItem>> = runCatching {
        apiClient.getYourAnniversaries().anniversaries.map { dto ->
            dto.toDomain().copy(
                game = resolveGameCovers(dto.game.toDomain(), dto.game),
            )
        }
    }

    override suspend fun getDecade(decade: String): Result<Pair<String, List<Game>>> = runCatching {
        val dto = apiClient.getDecade(decade)
        val games = dto.games.map { gameDto ->
            resolveGameCovers(gameDto.toDomain(), gameDto)
        }
        Pair(dto.label, games)
    }

    override suspend fun getEasyToComplete(): Result<List<AchievementGameItem>> = runCatching {
        apiClient.getEasyToComplete().games.map { dto ->
            dto.toDomain().copy(
                game = resolveGameCovers(dto.game.toDomain(), dto.game),
            )
        }
    }

    override suspend fun getHardestGames(): Result<List<AchievementGameItem>> = runCatching {
        apiClient.getHardestGames().games.map { dto ->
            dto.toDomain().copy(
                game = resolveGameCovers(dto.game.toDomain(), dto.game),
            )
        }
    }

    override suspend fun getAlmostDone(): Result<List<AlmostDoneGame>> = runCatching {
        apiClient.getAlmostDone().games.map { dto ->
            dto.toDomain().copy(
                game = resolveGameCovers(dto.game.toDomain(), dto.game),
            )
        }
    }

    override suspend fun getFreshChallenges(): Result<List<FreshChallengeGame>> = runCatching {
        apiClient.getFreshChallenges().games.map { dto ->
            dto.toDomain().copy(
                game = resolveGameCovers(dto.game.toDomain(), dto.game),
            )
        }
    }

    override suspend fun getActiveChallenges(): Result<List<ExploreChallenge>> = runCatching {
        apiClient.getActiveChallenges().challenges.map { it.toDomain() }
    }

    override suspend fun getFilteredGames(filters: Map<String, String>, page: Int, pageSize: Int): Result<List<Game>> = runCatching {
        apiClient.getFilteredGames(filters, page, pageSize).data.orEmpty().map { gameDto ->
            gameDto.toDomain().copy(
                coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
                heroUrl = apiClient.resolveUrl(gameDto.heroUrl),
                logoUrl = apiClient.resolveUrl(gameDto.logoUrl),
            )
        }
    }

    override suspend fun getSavedSearches(): Result<List<SavedSearch>> = runCatching {
        apiClient.getSavedSearches().map { it.toDomain() }
    }

    override suspend fun createSavedSearch(name: String, filters: Map<String, String>): Result<SavedSearch> = runCatching {
        apiClient.createSavedSearch(name, filters).toDomain()
    }

    override suspend fun deleteSavedSearch(id: String): Result<Unit> = runCatching {
        apiClient.deleteSavedSearch(id)
    }

    override suspend fun getWizardSteps(): Result<List<WizardStep>> = runCatching {
        apiClient.getWizardSteps().steps.map { it.toDomain() }
    }

    override suspend fun getWizardResults(mood: String, era: String, vibe: String): Result<WizardResults> = runCatching {
        val response = apiClient.getWizardResults(mood, era, vibe)
        WizardResults(
            games = response.games.map { dto ->
                val game = dto.toDomain()
                resolveGameCovers(game, dto)
            },
            title = response.title,
        )
    }

    override suspend fun getExplorerBadges(): Result<List<ExplorerBadge>> = runCatching {
        apiClient.getExplorerBadges().badges.orEmpty().map { it.toDomain() }
    }

    override suspend fun getCompletionistMap(): Result<CompletionistMap> = runCatching {
        apiClient.getCompletionistMap().toDomain()
    }

    private fun resolveEntityDetail(dto: DeveloperDetailResponseDto): DeveloperDetail =
        dto.toDomain().copy(
            heroUrl = apiClient.resolveUrl(dto.heroUrl),
            companyInfo = dto.companyInfo?.let { info ->
                info.toDomain().copy(
                    logoUrl = apiClient.resolveUrl(info.logoUrl),
                )
            },
            topGames = dto.topGames.map { gameDto ->
                gameDto.toDomain().copy(
                    coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
                )
            },
            userStats = dto.userStats?.let { stats ->
                stats.toDomain().copy(
                    mostPlayedGame = stats.mostPlayedGame?.let { gameDto ->
                        gameDto.toDomain().copy(
                            coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
                        )
                    },
                )
            },
            games = dto.games.map { gameDto ->
                gameDto.toDomain().copy(
                    coverUrl = apiClient.resolveUrl(gameDto.coverUrl),
                )
            },
        )

    private fun resolveGameCovers(game: Game, dto: GameDto): Game = game.copy(
        coverUrl = apiClient.resolveUrl(dto.coverUrl),
    )
}
