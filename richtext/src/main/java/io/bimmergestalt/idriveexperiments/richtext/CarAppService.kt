package io.bimmergestalt.idriveexperiments.richtext

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionReceiver
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import kotlinx.coroutines.flow.SharingStarted

class CarAppService: Service() {
	companion object {
		val ACTION_UICONNECTED = "io.bimmergestalt.idriveexperiments.richtext.uiConnected"
		val EXTRA_UICONNECTED = "UICONNECTED"
	}
	val carConnected = IDriveConnectionReceiver()
	var uiConnected = false
	var thread: CarThread? = null
	var app: CarApp? = null

	override fun onCreate() {
		super.onCreate()
		SecurityAccess.getInstance(applicationContext).connect()
	}

	/**
	 * When a car is connected, it will bind the Addon Service
	 */
	override fun onBind(intent: Intent): IBinder? {
		carConnected.onReceive(applicationContext, intent)
		if (intent.action == ACTION_UICONNECTED) {
			uiConnected = intent.getBooleanExtra(EXTRA_UICONNECTED, false)
		}
		tryStart()
		return null
	}

	/**
	 * If the thread crashes for any reason,
	 * opening the main app will trigger a Start on the Addon Services
	 * as a chance to reconnect
	 */
	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		super.onStartCommand(intent, flags, startId)
		intent ?: return START_NOT_STICKY
		carConnected.onReceive(applicationContext, intent)
		tryStart()
		return START_STICKY
	}

	fun tryStart() {
		if (carConnected.isConnected && uiConnected) {
			startThread()
		}
	}

	/**
	 * The car has disconnected, so forget the previous details
	 */
	override fun onUnbind(intent: Intent?): Boolean {
		IDriveConnectionStatus.reset()
		return super.onUnbind(intent)
	}

	/**
	 * Starts the thread for the car app, if it isn't running
	 */
	fun startThread() {
		val iDriveConnectionStatus = IDriveConnectionReceiver()
		val securityAccess = SecurityAccess.getInstance(applicationContext)
		if (iDriveConnectionStatus.isConnected &&
			securityAccess.isConnected() &&
			thread?.isAlive != true) {

			thread = CarThread("Richtext") {
				Log.i(TAG, "CarThread is ready, starting CarApp")

				app = CarApp(
					iDriveConnectionStatus,
					securityAccess,
					CarAppAssetResources(applicationContext, "basecoreOnlineServices"),
				)
			}
			thread?.start()
		} else if (thread?.isAlive != true) {
			if (thread?.isAlive != true) {
				Log.i(TAG, "Not connecting to car, because: iDriveConnectionStatus.isConnected=${iDriveConnectionStatus.isConnected} securityAccess.isConnected=${securityAccess.isConnected()}")
			} else {
				Log.d(TAG, "CarThread is still running, not trying to start it again")
			}
		}
	}

	override fun onDestroy() {
		super.onDestroy()

		app?.onDestroy()
	}
}