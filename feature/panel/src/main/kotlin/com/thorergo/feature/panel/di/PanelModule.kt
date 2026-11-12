package com.thorergo.feature.panel.di

import android.os.SystemClock
import com.thorergo.core.keyboard.output.OutputRouter
import com.thorergo.core.pinyin.PinyinComposer
import com.thorergo.core.pinyin.PinyinDictionary
import com.thorergo.feature.panel.PanelViewModel
import com.thorergo.feature.panel.service.PanelControl
import com.thorergo.feature.panel.service.PanelLauncher
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Named
import javax.inject.Singleton

/** Monotonic milliseconds, the same clock as touch event times. */
fun interface MonotonicClock {
    fun now(): Long
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PanelBindings {
    @Binds
    abstract fun panelControl(impl: PanelLauncher): PanelControl
}

@Module
@InstallIn(SingletonComponent::class)
internal object PanelModule {
    @Provides
    fun clock(): MonotonicClock = MonotonicClock(SystemClock::uptimeMillis)

    @Provides
    @Named(PanelViewModel.BACKGROUND)
    fun background(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun pinyinDictionary(): PinyinDictionary = PinyinDictionary.bundled()

    @Provides
    fun outputRouter(dictionary: PinyinDictionary): OutputRouter = OutputRouter(PinyinComposer(dictionary))
}
