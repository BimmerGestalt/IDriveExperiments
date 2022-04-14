package io.bimmergestalt.idriveexperiments.richtext

import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import io.bimmergestalt.idriveconnectkit.IDriveConnection
import io.bimmergestalt.idriveconnectkit.Utils.rhmi_setResourceCached
import io.bimmergestalt.idriveconnectkit.android.CarAppResources
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import io.bimmergestalt.idriveconnectkit.rhmi.*

val TAG = "RichText"
class CarApp(val iDriveConnectionStatus: IDriveConnectionStatus, securityAccess: SecurityAccess, val carAppResources: CarAppResources, ) {

	val carConnection: BMWRemotingServer
	val carApp: RHMIApplication
	val homeView: HomeView

	init {
		Log.i(TAG, "Starting connecting to car")
		val carappListener = CarAppListener()
		carConnection = IDriveConnection.getEtchConnection(iDriveConnectionStatus.host ?: "127.0.0.1", iDriveConnectionStatus.port ?: 8003, carappListener)
		val appCert = carAppResources.getAppCertificate(iDriveConnectionStatus.brand ?: "")?.readBytes()
		val sas_challenge = carConnection.sas_certificate(appCert)
		val sas_response = securityAccess.signChallenge(challenge = sas_challenge)
		carConnection.sas_login(sas_response)

		carApp = createRhmiApp()
		homeView = HomeView(carApp.states.values.first {HomeView.fits(it)})


		initWidgets()
		Log.i(TAG, "CarApp running")
	}

	private fun createRhmiApp(): RHMIApplication {
		// create the app in the car
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("io.bimmergestalt.idriveexperiments.richtext", BMWRemoting.VersionInfo(0, 1, 0), "io.bimmergestalt.idriveexperiments.richtext", "io.bimmergestalt"))
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppResources.getUiDescription())
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.TEXTDB, carAppResources.getTextsDB(iDriveConnectionStatus.brand ?: "common"))
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.IMAGEDB, carAppResources.getImagesDB(iDriveConnectionStatus.brand ?: "common"))
		carConnection.rhmi_initialize(rhmiHandle)

		// register for events from the car
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "io.bimmergestalt.idriveexperiments.richtext", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "io.bimmergestalt.idriveexperiments.richtext", -1, -1)

		return RHMIApplicationSynchronized(
			RHMIApplicationIdempotent(
				RHMIApplicationEtch(carConnection, rhmiHandle)
			), carConnection).apply {
			loadFromXML(carAppResources.getUiDescription()!!.readBytes())
		}
	}

	private fun initWidgets() {
		carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach {
			it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = homeView.state.id
		}
		homeView.initWidgets()
	}

	fun onDestroy() {
		try {
			Log.i(TAG, "Trying to shut down etch connection")
			IDriveConnection.disconnectEtchConnection(carConnection)
		} catch ( e: java.io.IOError) {
		} catch (e: RuntimeException) {}
	}

	inner class CarAppListener(): BaseBMWRemotingClient() {
		override fun rhmi_onActionEvent(handle: Int?, ident: String?, actionId: Int?, args: MutableMap<*, *>?) {
			try {
				carApp.actions[actionId]?.asRAAction()?.rhmiActionCallback?.onActionEvent(args)
				synchronized(carConnection) {
					carConnection.rhmi_ackActionEvent(handle, actionId, 1, true)
				}
			} catch (e: Exception) {
				Log.e(TAG, "Exception while calling onActionEvent handler!", e)
				synchronized(carConnection) {
					carConnection.rhmi_ackActionEvent(handle, actionId, 1, true)
				}
			}
		}

		override fun rhmi_onHmiEvent(handle: Int?, ident: String?, componentId: Int?, eventId: Int?, args: MutableMap<*, *>?) {
			try {
				// generic event handler
				carApp.states[componentId]?.onHmiEvent(eventId, args)
				carApp.components[componentId]?.onHmiEvent(eventId, args)
			} catch (e: Exception) {
				Log.e(TAG, "Received exception while handling rhmi_onHmiEvent", e)
			}
		}
	}
}