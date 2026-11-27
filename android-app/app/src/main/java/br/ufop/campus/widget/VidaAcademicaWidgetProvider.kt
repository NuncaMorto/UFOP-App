package br.ufop.campus.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RemoteViews
import br.ufop.campus.MainActivity
import br.ufop.campus.R
import java.time.LocalDate
import java.time.LocalTime

/**
 * AppWidgetProvider principal ("Vida Acadêmica" — responsivo 4×2 / 2×2).
 * Renderiza próxima aula, sala imediata, aula seguinte e próximo prazo pendente
 * sincronizados com as 9 paletas tonais e Modo Claro/Escuro do aplicativo.
 */
open class VidaAcademicaWidgetProvider : AppWidgetProvider() {

    protected open val forceCompactLayout: Boolean = false

    companion object {
        const val ACTION_UPDATE_WIDGET = "io.github.nuncamorto.vidaacademica.ACTION_UPDATE_WIDGET"
        private const val COMPACT_WIDTH_THRESHOLD_DP = 220

        private val AUTO_REFRESH_ACTIONS = setOf(
            ACTION_UPDATE_WIDGET,
            AppWidgetManager.ACTION_APPWIDGET_UPDATE,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_BOOT_COMPLETED
        )

        /**
         * Atualiza todos os widgets ativos (4×2 e 2×2) na tela inicial.
         */
        @JvmStatic
        fun requestWidgetUpdate(context: Context) {
            val appContext = context.applicationContext ?: context
            val appWidgetManager = AppWidgetManager.getInstance(appContext)

            val standardIds = appWidgetManager.getAppWidgetIds(
                ComponentName(appContext, VidaAcademicaWidgetProvider::class.java)
            )
            for (appWidgetId in standardIds) {
                updateAppWidget(
                    context = appContext,
                    appWidgetManager = appWidgetManager,
                    appWidgetId = appWidgetId,
                    forceCompact = false
                )
            }

            val compactIds = appWidgetManager.getAppWidgetIds(
                ComponentName(appContext, VidaAcademicaCompactWidgetProvider::class.java)
            )
            for (appWidgetId in compactIds) {
                updateAppWidget(
                    context = appContext,
                    appWidgetManager = appWidgetManager,
                    appWidgetId = appWidgetId,
                    forceCompact = true
                )
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            forceCompact: Boolean
        ) {
            val options = runCatching { appWidgetManager.getAppWidgetOptions(appWidgetId) }.getOrNull()
            val minWidthDp = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
            val useCompact = forceCompact || (minWidthDp in 1 until COMPACT_WIDTH_THRESHOLD_DP)

            val (snapshot, stateRoot) = WidgetScheduleLoader.loadCorridorSnapshot(
                context = context,
                today = LocalDate.now(),
                now = LocalTime.now()
            )
            val palette = WidgetAccentThemeHelper.resolvePalette(context, stateRoot)

            val agendaPendingIntent = buildTabPendingIntent(
                context = context,
                targetTab = MainActivity.WIDGET_TARGET_AGENDA,
                requestCode = 1001
            )
            val activitiesPendingIntent = buildTabPendingIntent(
                context = context,
                targetTab = MainActivity.WIDGET_TARGET_ACTIVITIES,
                requestCode = 1002
            )

            val views = if (useCompact) {
                bindCompactWidgetViews(
                    context = context,
                    snapshot = snapshot,
                    palette = palette,
                    agendaPendingIntent = agendaPendingIntent,
                    activitiesPendingIntent = activitiesPendingIntent
                )
            } else {
                bindStandardWidgetViews(
                    context = context,
                    snapshot = snapshot,
                    palette = palette,
                    agendaPendingIntent = agendaPendingIntent,
                    activitiesPendingIntent = activitiesPendingIntent
                )
            }

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        private fun bindStandardWidgetViews(
            context: Context,
            snapshot: WidgetCorridorSnapshot,
            palette: WidgetTonalPalette,
            agendaPendingIntent: PendingIntent,
            activitiesPendingIntent: PendingIntent
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_vida_academica)

            // Fundo Canvas tonal (zero borda)
            views.setInt(R.id.widget_root_bg, "setColorFilter", palette.canvas)

            // Cabeçalho em linha única: Pílula dia + resumo + contagem regressiva
            views.setInt(R.id.widget_day_pill_bg, "setColorFilter", palette.primary)
            views.setTextViewText(R.id.widget_day_pill_text, snapshot.dayPillText)
            views.setTextColor(R.id.widget_day_pill_text, palette.onPrimary)

            views.setTextViewText(R.id.widget_header_summary, snapshot.headerSummary)
            views.setTextColor(R.id.widget_header_summary, palette.ink)

            if (snapshot.countdownLabel.isNotBlank()) {
                views.setViewVisibility(R.id.widget_countdown_container, View.VISIBLE)
                views.setTextViewText(R.id.widget_countdown_text, snapshot.countdownLabel)
                if (snapshot.isOngoing) {
                    views.setInt(R.id.widget_countdown_bg, "setColorFilter", palette.liveBadgeBg)
                    views.setTextColor(R.id.widget_countdown_text, palette.liveBadgeFg)
                } else {
                    views.setInt(R.id.widget_countdown_bg, "setColorFilter", palette.surface2)
                    views.setTextColor(R.id.widget_countdown_text, palette.accentText)
                }
            } else {
                views.setViewVisibility(R.id.widget_countdown_container, View.GONE)
            }

            views.setOnClickPendingIntent(R.id.widget_header_container, agendaPendingIntent)

            val spotlight = snapshot.spotlightClass
            if (spotlight != null) {
                views.setViewVisibility(R.id.widget_active_schedule_container, View.VISIBLE)
                views.setViewVisibility(R.id.widget_free_day_container, View.GONE)

                // Card único elevado em Surface 1 (18dp, 0dp borda)
                views.setInt(R.id.widget_spotlight_bg, "setColorFilter", palette.surface1)
                views.setTextViewText(R.id.widget_spotlight_time, spotlight.timeRange)
                views.setTextColor(R.id.widget_spotlight_time, palette.accentText)

                views.setInt(R.id.widget_spotlight_room_bg, "setColorFilter", palette.surface2)
                views.setTextViewText(R.id.widget_spotlight_room, spotlight.roomPill)
                views.setTextColor(R.id.widget_spotlight_room, palette.ink)

                views.setTextViewText(R.id.widget_spotlight_title, spotlight.title)
                views.setTextColor(R.id.widget_spotlight_title, palette.ink)
                views.setOnClickPendingIntent(R.id.widget_spotlight_card, agendaPendingIntent)

                // Aula seguinte em linha aberta compacta (sem caixa)
                val following = snapshot.followingClass
                if (following != null) {
                    views.setViewVisibility(R.id.widget_following_row, View.VISIBLE)
                    views.setTextViewText(R.id.widget_following_time, following.startTime)
                    views.setTextColor(R.id.widget_following_time, palette.accentText)
                    views.setTextColor(R.id.widget_following_sep, palette.onSurfaceVariant)
                    views.setTextViewText(R.id.widget_following_title, following.title)
                    views.setTextColor(R.id.widget_following_title, palette.ink)
                    views.setTextViewText(R.id.widget_following_room, following.inlineRoom)
                    views.setTextColor(R.id.widget_following_room, palette.onSurfaceVariant)
                    views.setOnClickPendingIntent(R.id.widget_following_row, agendaPendingIntent)
                } else {
                    views.setViewVisibility(R.id.widget_following_row, View.GONE)
                }

                // Rodapé inteligente: próximo prazo pendente -> Atividades, senão 3ª aula -> Agenda
                val deadline = snapshot.nextDeadline
                val third = snapshot.thirdClass
                when {
                    deadline != null -> {
                        views.setViewVisibility(R.id.widget_footer_row, View.VISIBLE)
                        views.setTextViewText(
                            R.id.widget_footer_label,
                            "Próximo prazo: ${deadline.title}"
                        )
                        views.setTextColor(R.id.widget_footer_label, palette.onSurfaceVariant)
                        views.setTextViewText(R.id.widget_footer_trailing, deadline.shortDate)
                        views.setTextColor(R.id.widget_footer_trailing, palette.accentText)
                        views.setOnClickPendingIntent(R.id.widget_footer_row, activitiesPendingIntent)
                    }
                    third != null -> {
                        views.setViewVisibility(R.id.widget_footer_row, View.VISIBLE)
                        views.setTextViewText(
                            R.id.widget_footer_label,
                            "${third.startTime} · ${third.title}"
                        )
                        views.setTextColor(R.id.widget_footer_label, palette.onSurfaceVariant)
                        views.setTextViewText(R.id.widget_footer_trailing, third.inlineRoom)
                        views.setTextColor(R.id.widget_footer_trailing, palette.onSurfaceVariant)
                        views.setOnClickPendingIntent(R.id.widget_footer_row, agendaPendingIntent)
                    }
                    else -> {
                        views.setViewVisibility(R.id.widget_footer_row, View.GONE)
                    }
                }
            } else {
                // Estado Dia Livre (sem aulas ativas/restantes hoje)
                views.setViewVisibility(R.id.widget_active_schedule_container, View.GONE)
                views.setViewVisibility(R.id.widget_free_day_container, View.VISIBLE)
                views.setViewVisibility(R.id.widget_footer_row, View.GONE)

                views.setTextViewText(R.id.widget_free_day_title, snapshot.freeDayMessage)
                views.setTextColor(R.id.widget_free_day_title, palette.ink)
                views.setInt(R.id.widget_free_day_card_bg, "setColorFilter", palette.surface1)

                val deadline = snapshot.nextDeadline
                val nextDay = snapshot.nextSchoolDayClass
                when {
                    deadline != null -> {
                        views.setViewVisibility(R.id.widget_free_day_card, View.VISIBLE)
                        views.setTextViewText(R.id.widget_free_day_kicker, deadline.freeDayHeader)
                        views.setTextColor(R.id.widget_free_day_kicker, palette.accentText)
                        views.setTextViewText(R.id.widget_free_day_trailing, deadline.dueTime)
                        views.setTextColor(R.id.widget_free_day_trailing, palette.onSurfaceVariant)
                        views.setTextViewText(
                            R.id.widget_free_day_detail,
                            "${deadline.title} · ${deadline.dueTime}"
                        )
                        views.setTextColor(R.id.widget_free_day_detail, palette.ink)
                        views.setOnClickPendingIntent(R.id.widget_free_day_card, activitiesPendingIntent)
                    }
                    nextDay != null -> {
                        views.setViewVisibility(R.id.widget_free_day_card, View.VISIBLE)
                        views.setTextViewText(
                            R.id.widget_free_day_kicker,
                            "PRÓXIMA AULA · ${nextDay.dayBanner}"
                        )
                        views.setTextColor(R.id.widget_free_day_kicker, palette.accentText)
                        views.setTextViewText(R.id.widget_free_day_trailing, nextDay.roomLabel)
                        views.setTextColor(R.id.widget_free_day_trailing, palette.onSurfaceVariant)
                        views.setTextViewText(
                            R.id.widget_free_day_detail,
                            "${nextDay.startTime} · ${nextDay.title}"
                        )
                        views.setTextColor(R.id.widget_free_day_detail, palette.ink)
                        views.setOnClickPendingIntent(R.id.widget_free_day_card, agendaPendingIntent)
                    }
                    else -> {
                        views.setViewVisibility(R.id.widget_free_day_card, View.VISIBLE)
                        views.setTextViewText(R.id.widget_free_day_kicker, "AGENDA E PRAZOS")
                        views.setTextColor(R.id.widget_free_day_kicker, palette.accentText)
                        views.setTextViewText(R.id.widget_free_day_trailing, "")
                        views.setTextViewText(
                            R.id.widget_free_day_detail,
                            "Nenhuma aula ou prazo pendente"
                        )
                        views.setTextColor(R.id.widget_free_day_detail, palette.onSurfaceVariant)
                        views.setOnClickPendingIntent(R.id.widget_free_day_card, agendaPendingIntent)
                    }
                }
            }

            return views
        }

        private fun bindCompactWidgetViews(
            context: Context,
            snapshot: WidgetCorridorSnapshot,
            palette: WidgetTonalPalette,
            agendaPendingIntent: PendingIntent,
            activitiesPendingIntent: PendingIntent
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_vida_academica_small)

            views.setInt(R.id.widget_small_root_bg, "setColorFilter", palette.canvas)

            val spotlight = snapshot.spotlightClass
            if (spotlight != null) {
                views.setViewVisibility(R.id.widget_small_class_block, View.VISIBLE)
                views.setViewVisibility(R.id.widget_small_free_block, View.GONE)

                if (snapshot.isOngoing) {
                    views.setInt(R.id.widget_small_badge_bg, "setColorFilter", palette.liveBadgeBg)
                    views.setTextColor(R.id.widget_small_badge_text, palette.liveBadgeFg)
                } else {
                    views.setInt(R.id.widget_small_badge_bg, "setColorFilter", palette.primary)
                    views.setTextColor(R.id.widget_small_badge_text, palette.onPrimary)
                }
                views.setTextViewText(R.id.widget_small_badge_text, snapshot.compactCountdownBadge)
                views.setTextViewText(R.id.widget_small_top_right, snapshot.dayPillText)
                views.setTextColor(R.id.widget_small_top_right, palette.onSurfaceVariant)

                views.setTextViewText(R.id.widget_small_time, spotlight.startTime)
                views.setTextColor(R.id.widget_small_time, palette.accentText)

                views.setTextViewText(R.id.widget_small_title, spotlight.title)
                views.setTextColor(R.id.widget_small_title, palette.ink)

                views.setInt(R.id.widget_small_room_bg, "setColorFilter", palette.surface2)
                views.setTextViewText(R.id.widget_small_room, spotlight.smallRoomPill)
                views.setTextColor(R.id.widget_small_room, palette.ink)

                views.setOnClickPendingIntent(R.id.widget_small_root, agendaPendingIntent)
                views.setOnClickPendingIntent(R.id.widget_small_class_block, agendaPendingIntent)

                val following = snapshot.followingClass
                val deadline = snapshot.nextDeadline
                when {
                    following != null -> {
                        val extraCount = snapshot.remainingAfterSpotlightCount
                        val label = if (extraCount <= 1) {
                            "+1 aula às ${following.startTime}"
                        } else {
                            "+$extraCount aulas · ${following.startTime}"
                        }
                        views.setViewVisibility(R.id.widget_small_footer, View.VISIBLE)
                        views.setTextViewText(R.id.widget_small_footer, label)
                        views.setTextColor(R.id.widget_small_footer, palette.onSurfaceVariant)
                        views.setOnClickPendingIntent(R.id.widget_small_footer, agendaPendingIntent)
                    }
                    deadline != null -> {
                        views.setViewVisibility(R.id.widget_small_footer, View.VISIBLE)
                        views.setTextViewText(R.id.widget_small_footer, "Prazo: ${deadline.shortDate}")
                        views.setTextColor(R.id.widget_small_footer, palette.accentText)
                        views.setOnClickPendingIntent(R.id.widget_small_footer, activitiesPendingIntent)
                    }
                    else -> {
                        views.setViewVisibility(R.id.widget_small_footer, View.GONE)
                    }
                }
            } else {
                views.setViewVisibility(R.id.widget_small_class_block, View.GONE)
                views.setViewVisibility(R.id.widget_small_free_block, View.VISIBLE)
                views.setViewVisibility(R.id.widget_small_footer, View.GONE)

                views.setInt(R.id.widget_small_badge_bg, "setColorFilter", palette.primary)
                views.setTextViewText(R.id.widget_small_badge_text, snapshot.dayPillText)
                views.setTextColor(R.id.widget_small_badge_text, palette.onPrimary)

                views.setTextViewText(R.id.widget_small_top_right, "Dia livre")
                views.setTextColor(R.id.widget_small_top_right, palette.onSurfaceVariant)

                views.setTextViewText(R.id.widget_small_free_heading, snapshot.freeDayMessage)
                views.setTextColor(R.id.widget_small_free_heading, palette.ink)
                views.setInt(R.id.widget_small_free_card_bg, "setColorFilter", palette.surface1)

                val deadline = snapshot.nextDeadline
                val nextDay = snapshot.nextSchoolDayClass
                when {
                    deadline != null -> {
                        views.setTextViewText(R.id.widget_small_free_kicker, deadline.freeDayHeader)
                        views.setTextColor(R.id.widget_small_free_kicker, palette.accentText)
                        views.setTextViewText(
                            R.id.widget_small_free_title,
                            "${deadline.title} · ${deadline.dueTime}"
                        )
                        views.setTextColor(R.id.widget_small_free_title, palette.ink)
                        views.setOnClickPendingIntent(R.id.widget_small_root, activitiesPendingIntent)
                        views.setOnClickPendingIntent(R.id.widget_small_free_card, activitiesPendingIntent)
                    }
                    nextDay != null -> {
                        views.setTextViewText(R.id.widget_small_free_kicker, nextDay.dayBanner)
                        views.setTextColor(R.id.widget_small_free_kicker, palette.accentText)
                        views.setTextViewText(
                            R.id.widget_small_free_title,
                            "${nextDay.startTime} · ${nextDay.title}"
                        )
                        views.setTextColor(R.id.widget_small_free_title, palette.ink)
                        views.setOnClickPendingIntent(R.id.widget_small_root, agendaPendingIntent)
                        views.setOnClickPendingIntent(R.id.widget_small_free_card, agendaPendingIntent)
                    }
                    else -> {
                        views.setTextViewText(R.id.widget_small_free_kicker, "TUDO EM DIA")
                        views.setTextColor(R.id.widget_small_free_kicker, palette.accentText)
                        views.setTextViewText(
                            R.id.widget_small_free_title,
                            "Toque para abrir a agenda"
                        )
                        views.setTextColor(R.id.widget_small_free_title, palette.onSurfaceVariant)
                        views.setOnClickPendingIntent(R.id.widget_small_root, agendaPendingIntent)
                        views.setOnClickPendingIntent(R.id.widget_small_free_card, agendaPendingIntent)
                    }
                }
            }

            return views
        }

        private fun buildTabPendingIntent(
            context: Context,
            targetTab: String,
            requestCode: Int
        ): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_WIDGET_TARGET_TAB, targetTab)
            }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(
                context = context,
                appWidgetManager = appWidgetManager,
                appWidgetId = appWidgetId,
                forceCompact = forceCompactLayout
            )
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateAppWidget(
            context = context,
            appWidgetManager = appWidgetManager,
            appWidgetId = appWidgetId,
            forceCompact = forceCompactLayout
        )
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action in AUTO_REFRESH_ACTIONS) {
            requestWidgetUpdate(context)
            return
        }
        super.onReceive(context, intent)
    }
}

/**
 * Provider dedicado para variante compacta 2×2 ("Painel de Embarque").
 */
class VidaAcademicaCompactWidgetProvider : VidaAcademicaWidgetProvider() {
    override val forceCompactLayout: Boolean = true
}
