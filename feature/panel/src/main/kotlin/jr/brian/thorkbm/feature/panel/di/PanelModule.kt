package jr.brian.thorkbm.feature.panel.di

import android.os.SystemClock
import jr.brian.thorkbm.core.keyboard.output.OutputRouter
import jr.brian.thorkbm.core.pinyin.PinyinComposer
import jr.brian.thorkbm.core.pinyin.PinyinDictionary
import jr.brian.thorkbm.core.swipe.SwipeDecoder
import jr.brian.thorkbm.core.swipe.SwipeLexicon
import jr.brian.thorkbm.feature.panel.PanelViewModel
import jr.brian.thorkbm.feature.panel.service.PanelControl
import jr.brian.thorkbm.feature.panel.service.PanelLauncher
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
    @Singleton
    fun swipeDecoder(): SwipeDecoder = SwipeDecoder(SwipeLexicon.bundled())

    @Provides
    fun outputRouter(dictionary: PinyinDictionary): OutputRouter = OutputRouter(PinyinComposer(dictionary))
}
