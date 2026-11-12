package com.thorergo.feature.setup

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject

/** Platform permission checks; these do not notify, so the screen re-checks on resume. */
interface SystemPermissions {
    fun canDrawOverlays(): Boolean

    fun canPostNotifications(): Boolean
}

internal class AndroidSystemPermissions @Inject constructor(
    @ApplicationContext private val context: Context,
) : SystemPermissions {
    override fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    override fun canPostNotifications(): Boolean =
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SetupModule {
    @Binds
    abstract fun permissions(impl: AndroidSystemPermissions): SystemPermissions
}
