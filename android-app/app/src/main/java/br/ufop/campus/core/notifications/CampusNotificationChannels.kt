package br.ufop.campus.core.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Define e cria os 5 canais locais de notificação do Vida Acadêmica em PT-BR.
 */
object CampusNotificationChannels {

    const val CHANNEL_LIVE_CARD = "campus_live_card"
    const val CHANNEL_CLASS_REMINDERS = "campus_class_reminders"
    const val CHANNEL_DEADLINES = "campus_deadlines"
    const val CHANNEL_ABSENCE_ALERTS = "campus_absence_alerts"
    const val CHANNEL_MORNING_SUMMARY = "campus_morning_summary"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val appContext = context.applicationContext ?: context
        val manager = appContext.getSystemService(NotificationManager::class.java) ?: return

        val liveChannel = NotificationChannel(
            CHANNEL_LIVE_CARD,
            "Vida Acadêmica · Ao vivo",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Contagem regressiva e progresso contínuo da aula atual na tela de bloqueio."
            setShowBadge(false)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val classChannel = NotificationChannel(
            CHANNEL_CLASS_REMINDERS,
            "Vida Acadêmica · Aulas",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Lembretes antes do início das aulas com confirmação rápida de presença e mapa."
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val deadlineChannel = NotificationChannel(
            CHANNEL_DEADLINES,
            "Vida Acadêmica · Prazos",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Avisos de provas, trabalhos e entregas pendentes (3 dias e 1 dia antes às 09:00)."
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val absenceChannel = NotificationChannel(
            CHANNEL_ABSENCE_ALERTS,
            "Vida Acadêmica · Faltas",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alertas pontuais ao atingir 65% ou 85% do limite de faltas da disciplina."
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        val morningChannel = NotificationChannel(
            CHANNEL_MORNING_SUMMARY,
            "Vida Acadêmica · Resumo do dia",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Resumo matinal às 07:30 em dias letivos com aula programada."
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }

        manager.createNotificationChannels(
            listOf(liveChannel, classChannel, deadlineChannel, absenceChannel, morningChannel)
        )
    }

    fun hasRuntimeNotificationPermission(context: Context): Boolean {
        val appContext = context.applicationContext ?: context
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return runCatching {
            NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        }.getOrDefault(true)
    }
}
