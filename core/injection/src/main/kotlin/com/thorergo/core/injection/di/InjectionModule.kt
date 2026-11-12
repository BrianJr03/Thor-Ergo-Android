package com.thorergo.core.injection.di

import com.thorergo.core.injection.CompositeInjector
import com.thorergo.core.injection.InputInjector
import com.thorergo.core.injection.PanelPowerController
import com.thorergo.core.injection.ShizukuPanelPowerController
import com.thorergo.core.injection.PointerModeConfig
import com.thorergo.core.injection.shizuku.ShizukuAccess
import com.thorergo.core.injection.shizuku.ShizukuConnection
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

/** A single thread for binder calls, so injected events keep their order. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class InjectionDispatcher

@Module
@InstallIn(SingletonComponent::class)
internal abstract class InjectionModule {
    @Binds
    abstract fun inputInjector(impl: CompositeInjector): InputInjector

    @Binds
    abstract fun panelPower(impl: ShizukuPanelPowerController): PanelPowerController

    @Binds
    abstract fun shizukuAccess(impl: ShizukuConnection): ShizukuAccess

    @Binds
    abstract fun pointerMode(impl: CompositeInjector): PointerModeConfig

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun applicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        @Provides
        @Singleton
        @InjectionDispatcher
        fun injectionDispatcher(): CoroutineDispatcher = Dispatchers.IO.limitedParallelism(1)
    }
}
