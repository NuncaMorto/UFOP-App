package br.ufop.campus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.RemoteViews
import br.ufop.campus.MainActivity
import br.ufop.campus.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * AppWidgetProvider para o widget "Vida Acadêmica" (Horários de Hoje).
 * Sincroniza automaticamente a lista em mudanças de estado, data, fuso, hora e boot.
 */
class VidaAcademicaWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_UPDATE_WIDGET = "br.ufop.campus.ACTION_UPDATE_WIDGET"
        private val SYNC_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm")

        private val AUTO_REFRESH_ACTIONS = setOf(
            ACTION_UPDATE_WIDGET,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED
        )

        /**
         * Atualiza todos os widgets ativos na tela inicial (cabeçalho + lista de horários).
         */
        @JvmStatic
        fun requestWidgetUpdate(context: Context) {
            val appContext = context.applicationContext ?: context
            val appWidgetManager = AppWidgetManager.getInstance(appContext)
            val componentName = ComponentName(appContext, VidaAcademicaWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (appWidgetId in appWidgetIds) {
                updateAppWidget(appContext, appWidgetManager, appWidgetId)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val views = RemoteViews(context.packageName, R.layout.widget_vida_academica)
            val accentPalette = WidgetAccentThemeHelper.resolvePalette(context)

            // Cabeçalho: "Hoje · <dia da semana>" (ex: "Hoje · Quarta-feira")
            val ptBr = Locale("pt", "BR")
            val today = LocalDate.now()
            val rawDayName = today.dayOfWeek.getDisplayName(TextStyle.FULL, ptBr)
            val dayName = rawDayName.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(ptBr) else it.toString()
            }
            views.setTextViewText(R.id.widget_header_day, "Hoje · $dayName")

            // Subtítulo com resumo do dia + horário de sincronização
            val snapshot = WidgetScheduleLoader.loadTodaySchedule(context, today)
            val syncTime = LocalTime.now().format(SYNC_TIME_FORMATTER)
            val summaryText = buildHeaderSubtitle(
                classesCount = snapshot.classesCount,
                commitmentsCount = snapshot.commitmentsCount,
                syncTime = syncTime
            )
            views.setTextViewText(R.id.widget_header_app_title, summaryText)
            views.setTextColor(R.id.widget_header_app_title, accentPalette.headerSubtitleColor)

            // Tocar no cabeçalho, no estado vazio ou em um item abre a MainActivity
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_header_container, openAppPendingIntent)
            views.setOnClickPendingIntent(R.id.widget_empty_view, openAppPendingIntent)
            views.setPendingIntentTemplate(R.id.widget_schedule_list, openAppPendingIntent)

            views.setEmptyView(R.id.widget_schedule_list, R.id.widget_empty_view)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val collectionBuilder = RemoteViews.RemoteCollectionItems.Builder()
                    .setHasStableIds(true)
                    .setViewTypeCount(1)
                for (item in snapshot.items) {
                    collectionBuilder.addItem(
                        item.stableId,
                        WidgetScheduleItemRenderer.buildItemRemoteViews(
                            context = context,
                            item = item,
                            timeAccentColor = accentPalette.itemTimeColor
                        )
                    )
                }
                views.setRemoteAdapter(R.id.widget_schedule_list, collectionBuilder.build())
                appWidgetManager.updateAppWidget(appWidgetId, views)
            } else {
                bindLegacyRemoteAdapter(
                    context = context,
                    appWidgetManager = appWidgetManager,
                    appWidgetId = appWidgetId,
                    views = views
                )
            }
        }

        @Suppress("DEPRECATION")
        private fun bindLegacyRemoteAdapter(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            views: RemoteViews
        ) {
            val serviceIntent = Intent(context, VidaAcademicaWidgetService::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                data = Uri.parse("vidaacademica://widget/schedule/$appWidgetId")
            }
            views.setRemoteAdapter(R.id.widget_schedule_list, serviceIntent)
            appWidgetManager.updateAppWidget(appWidgetId, views)
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.widget_schedule_list)
        }

        private fun buildHeaderSubtitle(
            classesCount: Int,
            commitmentsCount: Int,
            syncTime: String
        ): String {
            return when {
                classesCount > 0 && commitmentsCount > 0 -> {
                    val cLabel = if (classesCount == 1) "1 aula" else "$classesCount aulas"
                    val eLabel = if (commitmentsCount == 1) "1 evento" else "$commitmentsCount eventos"
                    "$cLabel · $eLabel · $syncTime"
                }
                classesCount > 0 -> {
                    val cLabel = if (classesCount == 1) "1 aula" else "$classesCount aulas"
                    "$cLabel · Atualizado $syncTime"
                }
                commitmentsCount > 0 -> {
                    val eLabel = if (commitmentsCount == 1) "1 compromisso" else "$commitmentsCount compromissos"
                    "$eLabel · $syncTime"
                }
                else -> "Vida Acadêmica · Atualizado $syncTime"
            }
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in AUTO_REFRESH_ACTIONS) {
            requestWidgetUpdate(context)
            return
        }
        super.onReceive(context, intent)
    }
}
