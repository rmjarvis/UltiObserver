package rmjarvis.ultiobserver

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.wear.ongoing.OngoingActivity
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import rmjarvis.ultiobserver.wearprotocol.WearProtocolCodec
import rmjarvis.ultiobserver.wearprotocol.WEAR_ONGOING_GAME_PATH
import rmjarvis.ultiobserver.wearprotocol.WearOngoingGameStatus

/** Keep the return-to-game shortcut current when the watch Activity is not listening. */
class OngoingGameService : WearableListenerService() {
    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (event.type == DataEvent.TYPE_DELETED) {
                getSystemService(NotificationManager::class.java).cancel(ONGOING_GAME_NOTIFICATION_ID)
            } else {
                val status = WearProtocolCodec.decode(
                    WearOngoingGameStatus.serializer(), event.dataItem.data!!,
                )
                updateOngoingGame(status.hasCurrentGame)
            }
        }
    }
}

/** Reconcile the shortcut after returning to the app or changing notification permission. */
internal fun Context.refreshOngoingGame() {
    Wearable.getDataClient(this)
        .getDataItems(Uri.parse("wear://*$WEAR_ONGOING_GAME_PATH"), DataClient.FILTER_LITERAL)
        .addOnSuccessListener { items ->
            try {
                val hasCurrentGame = items.any {
                    WearProtocolCodec.decode(
                        WearOngoingGameStatus.serializer(), it.data!!,
                    ).hasCurrentGame
                }
                updateOngoingGame(hasCurrentGame)
            } finally {
                items.release()
            }
        }
}

/** Retain one quiet shortcut while a current game, including its final score, is on the watch. */
internal fun Context.updateOngoingGame(hasCurrentGame: Boolean) {
    val notifications = getSystemService(NotificationManager::class.java)
    if (!hasCurrentGame) {
        notifications.cancel(ONGOING_GAME_NOTIFICATION_ID)
        return
    }
    if (!notifications.areNotificationsEnabled()) return
    if (notifications.activeNotifications.any { it.id == ONGOING_GAME_NOTIFICATION_ID }) return

    notifications.createNotificationChannel(
        NotificationChannel(ONGOING_GAME_CHANNEL_ID, "Current game", NotificationManager.IMPORTANCE_LOW)
            .apply {
                setSound(null, null)
                enableVibration(false)
            },
    )
    val returnToGame = PendingIntent.getActivity(
        this,
        0,
        Intent(this, WatchActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val notification = NotificationCompat.Builder(this, ONGOING_GAME_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_ongoing_game)
        .setContentTitle("UltiObserver")
        .setContentText("Return to game")
        .setContentIntent(returnToGame)
        .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
        .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        .setLocalOnly(true)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
    OngoingActivity.Builder(this, ONGOING_GAME_NOTIFICATION_ID, notification)
        .setStaticIcon(R.drawable.ic_ongoing_game)
        .setTouchIntent(returnToGame)
        .build()
        .apply(this)
    notifications.notify(ONGOING_GAME_NOTIFICATION_ID, notification.build())
}

private const val ONGOING_GAME_CHANNEL_ID = "current_game"
