package com.thorergo.core.data.di

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStoreFile
import com.thorergo.core.data.DataStoreSettingsRepository
import com.thorergo.core.data.SettingsRepository
import com.thorergo.core.data.SettingsSerializer
import com.thorergo.core.model.Settings
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun settingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    companion object {
        @Provides
        @Singleton
        fun settingsStore(@ApplicationContext context: Context): DataStore<Settings> = DataStoreFactory.create(
            serializer = SettingsSerializer,
            corruptionHandler = ReplaceFileCorruptionHandler { error ->
                Log.w("ThorErgo", "Settings file was unreadable; using defaults", error)
                Settings()
            },
            produceFile = { context.dataStoreFile("settings.json") },
        )
    }
}
