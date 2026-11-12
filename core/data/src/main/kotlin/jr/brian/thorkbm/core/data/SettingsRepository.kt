package jr.brian.thorkbm.core.data

import jr.brian.thorkbm.core.model.Settings
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** The persisted settings, always normalised. */
    val settings: Flow<Settings>

    /**
     * Atomically applies [transform] and persists the normalised result.
     * On failure the stored value is unchanged and the error is returned.
     */
    suspend fun update(transform: (Settings) -> Settings): Result<Settings>
}
