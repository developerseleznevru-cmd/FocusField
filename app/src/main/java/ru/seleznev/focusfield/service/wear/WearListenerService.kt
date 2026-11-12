package ru.seleznev.focusfield.service.wear

import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import ru.seleznev.focusfield.FocusFieldApp
import ru.seleznev.focusfield.data.model.WearHealthData

/**
 * Приём данных о пульсе и активности с Wear OS устройства.
 * Устройство-компаньон (часы) публикует DataMap по пути /focusfield/health:
 *   - «heartrate» (Int)
 *   - «steps» (Int)
 *   - «timestamp» (Long)
 */
class WearListenerService : WearableListenerService() {

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        val store = healthStore() ?: return
        for (event in dataEvents) {
            if (event.type != DataEvent.TYPE_CHANGED) continue
            if (event.dataItem.uri.path != HEALTH_PATH) continue
            val map = DataMapItem.fromDataItem(event.dataItem).dataMap
            val heartRate = map.getInt(KEY_HEART_RATE, 0)
            val steps = map.getInt(KEY_STEPS, 0)
            val timestamp = map.getLong(KEY_TIMESTAMP, System.currentTimeMillis())
            if (heartRate > 0) {
                store.update(WearHealthData(heartRate = heartRate, steps = steps, timestamp = timestamp))
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != HEALTH_PATH) return
        val store = healthStore() ?: return
        val map = com.google.android.gms.wearable.DataMap.fromByteArray(event.data)
        val heartRate = map.getInt(KEY_HEART_RATE, 0)
        if (heartRate > 0) {
            store.update(
                WearHealthData(
                    heartRate = heartRate,
                    steps = map.getInt(KEY_STEPS, 0),
                    timestamp = map.getLong(KEY_TIMESTAMP, System.currentTimeMillis())
                )
            )
        }
    }

    private fun healthStore(): WearHealthStore? =
        (applicationContext as? FocusFieldApp)?.container?.wearHealthStore

    companion object {
        const val HEALTH_PATH = "/focusfield/health"
        const val KEY_HEART_RATE = "heartrate"
        const val KEY_STEPS = "steps"
        const val KEY_TIMESTAMP = "timestamp"
    }
}
