package com.oakiha.audia.data.repository

import com.oakiha.audia.data.database.TransitionDao
import com.oakiha.audia.data.database.TransitionRuleEntity
import com.oakiha.audia.data.model.TransitionResolution
import com.oakiha.audia.data.model.TransitionRule
import com.oakiha.audia.data.model.TransitionSettings
import com.oakiha.audia.data.model.TransitionSource
import com.oakiha.audia.data.preferences.UserPreferencesRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TransitionRepositoryImpl @Inject constructor(
    private val transitionDao: TransitionDao,
    private val userPreferences: UserPreferencesRepository
) : TransitionRepository {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun resolveTransitionSettings(
        BooklistId: String,
        fromTrackId: String,
        toTrackId: String
    ): Flow<TransitionResolution> {
        // Chain the lookups according to priority: specific -> Booklist -> global
        return transitionDao.getSpecificRule(BooklistId, fromTrackId, toTrackId)
            .flatMapLatest { specificRule ->
                if (specificRule != null) {
                    flowOf(
                        TransitionResolution(
                            settings = specificRule.settings,
                            source = TransitionSource.Booklist_SPECIFIC,
                        )
                    )
                } else {
                    transitionDao.getBooklistDefaultRule(BooklistId).flatMapLatest { BooklistRule ->
                        if (BooklistRule != null) {
                            flowOf(
                                TransitionResolution(
                                    settings = BooklistRule.settings,
                                    source = TransitionSource.Booklist_DEFAULT,
                                )
                            )
                        } else {
                            userPreferences.globalTransitionSettingsFlow.map { settings ->
                                TransitionResolution(
                                    settings = settings,
                                    source = TransitionSource.GLOBAL_DEFAULT,
                                )
                            }
                        }
                    }
                }
            }
    }

    override fun getAllRulesForBooklist(BooklistId: String): Flow<List<TransitionRule>> {
        return transitionDao.getAllRulesForBooklist(BooklistId).map { entities ->
            entities.map { it.toModel() }
        }
    }

    override fun getBooklistDefaultRule(BooklistId: String): Flow<TransitionRule?> {
        return transitionDao.getBooklistDefaultRule(BooklistId).map { entity ->
            entity?.toModel()
        }
    }

    override suspend fun saveRule(rule: TransitionRule) {
        transitionDao.setRule(rule.toEntity())
    }

    override suspend fun deleteRule(ruleId: Long) {
        transitionDao.deleteRule(ruleId)
    }

    override suspend fun deleteBooklistDefaultRule(BooklistId: String) {
        transitionDao.deleteBooklistDefaultRule(BooklistId)
    }

    override fun getGlobalSettings(): Flow<TransitionSettings> {
        return userPreferences.globalTransitionSettingsFlow
    }

    override suspend fun saveGlobalSettings(settings: TransitionSettings) {
        userPreferences.saveGlobalTransitionSettings(settings)
    }

    // --- Mappers ---

    private fun TransitionRuleEntity.toModel(): TransitionRule {
        return TransitionRule(
            id = this.id,
            BooklistId = this.BooklistId,
            fromTrackId = this.fromTrackId,
            toTrackId = this.toTrackId,
            settings = this.settings
        )
    }

    private fun TransitionRule.toEntity(): TransitionRuleEntity {
        // The ID is included for updates. If it's the default 0, Room treats it as a new entry for auto-generation.
        // The unique index on (BooklistId, from, to) ensures upsert logic works correctly.
        return TransitionRuleEntity(
            id = this.id,
            BooklistId = this.BooklistId,
            fromTrackId = this.fromTrackId,
            toTrackId = this.toTrackId,
            settings = this.settings
        )
    }
}
