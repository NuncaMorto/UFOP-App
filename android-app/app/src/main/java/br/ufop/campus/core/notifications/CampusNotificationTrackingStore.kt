package br.ufop.campus.core.notifications

import android.content.Context
import android.content.SharedPreferences

/**
 * Armazena metadados locais de rastreamento de alarmes agendados e deduplicação de alertas de faltas
 * em SharedPreferences dedicado, sem disparar gravação ou recursão no repositório acadêmico.
 */
internal object CampusNotificationTrackingStore {

    private const val PREFS_NAME = "vida_academica_notifications_state"
    private const val KEY_SCHEDULED_ALARMS = "scheduled_alarms_v1"
    private const val KEY_ABSENCE_ALERTED_KEYS = "absence_alerted_keys_v1"
    private const val KEY_ACTIVE_LIVE_SLOT = "active_live_slot_v1"

    data class TrackedAlarmSpec(
        val requestCode: Int,
        val action: String
    )

    private fun prefs(context: Context): SharedPreferences =
        (context.applicationContext ?: context).getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadScheduledAlarms(context: Context): List<TrackedAlarmSpec> {
        val raw = runCatching {
            prefs(context).getStringSet(KEY_SCHEDULED_ALARMS, emptySet()).orEmpty()
        }.getOrDefault(emptySet())
        return raw.mapNotNull { entry ->
            val sep = entry.indexOf('|')
            if (sep <= 0) return@mapNotNull null
            val code = entry.substring(0, sep).toIntOrNull() ?: return@mapNotNull null
            val action = entry.substring(sep + 1)
            TrackedAlarmSpec(requestCode = code, action = action)
        }
    }

    fun saveScheduledAlarms(context: Context, specs: List<TrackedAlarmSpec>) {
        val encoded = specs.map { "${it.requestCode}|${it.action}" }.toSet()
        runCatching {
            prefs(context).edit()
                .putStringSet(KEY_SCHEDULED_ALARMS, encoded)
                .apply()
        }
    }

    fun addScheduledAlarm(context: Context, spec: TrackedAlarmSpec) {
        val current = loadScheduledAlarms(context).toMutableList()
        if (current.none { it.requestCode == spec.requestCode && it.action == spec.action }) {
            current.add(spec)
            saveScheduledAlarms(context, current)
        }
    }

    fun loadAbsenceAlertedKeys(context: Context): Set<String> =
        runCatching {
            prefs(context).getStringSet(KEY_ABSENCE_ALERTED_KEYS, emptySet()).orEmpty().toSet()
        }.getOrDefault(emptySet())

    fun saveAbsenceAlertedKeys(context: Context, keys: Set<String>) {
        runCatching {
            prefs(context).edit()
                .putStringSet(KEY_ABSENCE_ALERTED_KEYS, keys.toSet())
                .apply()
        }
    }

    fun getActiveLiveSlotId(context: Context): String? =
        runCatching { prefs(context).getString(KEY_ACTIVE_LIVE_SLOT, null) }.getOrNull()

    fun setActiveLiveSlotId(context: Context, slotKey: String?) {
        runCatching {
            prefs(context).edit().apply {
                if (slotKey.isNullOrBlank()) {
                    remove(KEY_ACTIVE_LIVE_SLOT)
                } else {
                    putString(KEY_ACTIVE_LIVE_SLOT, slotKey)
                }
            }.apply()
        }
    }

    fun clearAll(context: Context) {
        runCatching {
            prefs(context).edit().clear().commit()
        }
    }
}
