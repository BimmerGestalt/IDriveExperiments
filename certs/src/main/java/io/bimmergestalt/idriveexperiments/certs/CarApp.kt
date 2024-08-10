package io.bimmergestalt.idriveexperiments.certs

import android.util.Log
import de.bmw.idrive.BMWRemoting
import de.bmw.idrive.BMWRemotingServer
import de.bmw.idrive.BaseBMWRemotingClient
import io.bimmergestalt.idriveconnectkit.IDriveConnection
import io.bimmergestalt.idriveconnectkit.Utils.rhmi_setResourceCached
import io.bimmergestalt.idriveconnectkit.android.CarAppAssetResources
import io.bimmergestalt.idriveconnectkit.android.IDriveConnectionStatus
import io.bimmergestalt.idriveconnectkit.android.security.SecurityAccess
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIApplication
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIApplicationEtch
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIApplicationIdempotent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIApplicationSynchronized


val TAG = "CarMap"
class CarApp(
	val iDriveConnectionStatus: IDriveConnectionStatus,
	securityAccess: SecurityAccess,
	val carAppResources: CarAppAssetResources
) {

	val carConnection: BMWRemotingServer
	var carApp: RHMIApplication? = null

	init {
		val carappListener = CarAppListener()
		carConnection = IDriveConnection.getEtchConnection(
			iDriveConnectionStatus.host ?: "127.0.0.1",
			iDriveConnectionStatus.port ?: 8003,
			carappListener
		)
		try {
			MainViewModel.log("Starting connecting to car")
//			val appCert = carAppResources.getAppCertificate("common").readBytes()
			val rawCert = carAppResources.loadFile("carapplications/${carAppResources.name}/rhmi/common/${carAppResources.name}.p7b")!!.readBytes()
			MainViewModel.log("Presenting cert")
			val sas_challenge = carConnection.sas_certificate(rawCert)
			MainViewModel.log("Signing cert")
			val sas_response = securityAccess.signChallenge(challenge = sas_challenge)
			MainViewModel.log("Logging in")
			carConnection.sas_login(sas_response)

			carApp = createRhmiApp()
			carApp?.loadFromXML(carAppResources.getUiDescription()!!.readBytes())

			MainViewModel.log("CarApp running")
		} catch (e: Exception) {
			MainViewModel.log("Exception: $e")
			Log.w(TAG, e)
		}
	}

	private fun createRhmiApp(): RHMIApplication {
		// create the app in the car
		MainViewModel.log("Creating RHMI")
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("io.bimmergestalt.idriveexperiments.certs", BMWRemoting.VersionInfo(0, 1, 0), "io.bimmergestalt.idriveexperiments.certs", "io.bimmergestalt"))
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppResources.getUiDescription())
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.TEXTDB, carAppResources.getTextsDB("mini"))
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.IMAGEDB, carAppResources.getImagesDB("mini"))
		carConnection.rhmi_initialize(rhmiHandle)

		MainViewModel.log("Created RHMI")

		// register for events from the car
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "io.bimmergestalt.idriveexperiments.certs", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "io.bimmergestalt.idriveexperiments.certs", -1, -1)

		return RHMIApplicationSynchronized(
			RHMIApplicationIdempotent(
				RHMIApplicationEtch(carConnection, rhmiHandle)
			), carConnection)
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
			Log.i(TAG, "Received rhmi_onActionEvent: handle=$handle ident=$ident actionId=$actionId")
			val carApp = carApp ?: return
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
			val msg = "Received rhmi_onHmiEvent: handle=$handle ident=$ident componentId=$componentId eventId=$eventId args=${args?.toString()}"
			Log.i(TAG, msg)
			val carApp = carApp ?: return
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