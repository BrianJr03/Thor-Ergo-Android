package jr.brian.thorkbm.core.update.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import jr.brian.thorkbm.core.update.AppUpdater
import jr.brian.thorkbm.core.update.GitHubAppUpdater

@Module
@InstallIn(SingletonComponent::class)
internal abstract class UpdateModule {
    @Binds
    abstract fun appUpdater(impl: GitHubAppUpdater): AppUpdater
}
