package jr.brian.thorkbm

import android.content.Context
import android.content.Intent
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import jr.brian.thorkbm.feature.panel.service.PanelControl
import jr.brian.thorkbm.feature.settings.AppActions
import javax.inject.Inject

/** Settings → Setup actions. Works from the panel overlay too, which has no activity of its own. */
internal class AndroidAppActions @Inject constructor(
    @ApplicationContext private val context: Context,
    private val panel: PanelControl,
) : AppActions {
    override fun openSetup() {
        context.startActivity(MainActivity.setupIntent(context).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun stopPanel() = panel.stop()
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class AppModule {
    @Binds
    abstract fun appActions(impl: AndroidAppActions): AppActions
}
