package br.ufop.campus.core.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import br.ufop.campus.MainActivity

/**
 * Recebe o resultado da sessão de instalação do [PackageInstaller].
 */
class AppUpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
        val confirmIntent: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }

        AppUpdateManager.onInstallStatusFromReceiver(
            context = context,
            status = status,
            statusMessage = message,
            confirmIntent = confirmIntent
        )

        if (status == PackageInstaller.STATUS_SUCCESS) {
            launchMainActivityCleanly(context)
        }
    }
}

/**
 * Executado automaticamente pelo Android após o app ser atualizado (`MY_PACKAGE_REPLACED`).
 * Limpa APKs temporários em cache e reabre o app mantendo 100% dos dados do aluno intactos.
 */
class AppPackageReplacedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val appContext = context.applicationContext
        AppUpdateManager.purgeCachedApks(appContext)
        val shouldRelaunch = AppUpdateManager.consumePendingAutoRelaunch(appContext)
        if (shouldRelaunch) {
            launchMainActivityCleanly(appContext)
        }
    }
}

private fun launchMainActivityCleanly(context: Context) {
    val appContext = context.applicationContext
    val launchIntent = appContext.packageManager
        .getLaunchIntentForPackage(appContext.packageName)
        ?: Intent(appContext, MainActivity::class.java)
    launchIntent.addFlags(
        Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    )
    runCatching {
        appContext.startActivity(launchIntent)
    }
}
