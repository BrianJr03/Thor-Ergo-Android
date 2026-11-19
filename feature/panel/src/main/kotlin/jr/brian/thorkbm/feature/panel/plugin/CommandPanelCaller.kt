package jr.brian.thorkbm.feature.panel.plugin

import android.content.pm.PackageManager
import java.security.MessageDigest

/** Decides whether a message really comes from Command Panel. */
fun interface CallerCheck {
    fun isCommandPanel(uid: Int): Boolean
}

/**
 * The uid must belong to one of Command Panel's packages, and every current signer of that package
 * must be Command Panel's release key. [anySigner] drops the key part, for debug builds of ThorKBM
 * talking to a debug Command Panel.
 */
class CommandPanelCaller(
    private val packages: PackageManager,
    private val anySigner: Boolean,
) : CallerCheck {
    override fun isCommandPanel(uid: Int): Boolean {
        val names = packages.getPackagesForUid(uid) ?: return false
        return names.any { it in PanelPluginContract.COMMAND_PANEL_PACKAGES && (anySigner || signedByCommandPanel(it)) }
    }

    private fun signedByCommandPanel(name: String): Boolean {
        val signing = try {
            packages.getPackageInfo(name, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } ?: return false
        val signers = signing.apkContentsSigners ?: return false
        return signers.isNotEmpty() && signers.all { sha256(it.toByteArray()) == PanelPluginContract.COMMAND_PANEL_CERT_SHA256 }
    }

    private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
