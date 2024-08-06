package io.bimmergestalt.idriveexperiments.map

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
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIComponent
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIModel
import io.bimmergestalt.idriveconnectkit.rhmi.RHMIState
import io.bimmergestalt.idriveconnectkit.rhmi.VisibleCallback


val TAG = "CarMap"
class CarApp(
	val iDriveConnectionStatus: IDriveConnectionStatus,
	securityAccess: SecurityAccess,
	val carAppResources: CarAppAssetResources
) {

	val carConnection: BMWRemotingServer
	val carApp: RHMIApplication
	var mapHandle: Int = 0
	val mapView: RHMIState
	val mapLabel: RHMIComponent.Label
	val mapResults: RHMIModel.RaListModel

	init {
		try {
			MainViewModel.log("Starting connecting to car")
			val carappListener = CarAppListener()
			carConnection = IDriveConnection.getEtchConnection(
				iDriveConnectionStatus.host ?: "127.0.0.1",
				iDriveConnectionStatus.port ?: 8003,
				carappListener
			)
			val appCert = carAppResources.getAppCertificate("bmw").readBytes()
			MainViewModel.log("Presenting cert")
			val sas_challenge = carConnection.sas_certificate(appCert)
			MainViewModel.log("Signing cert")
			val sas_response = securityAccess.signChallenge(challenge = sas_challenge)
			MainViewModel.log("Logging in")
			carConnection.sas_login(sas_response)

			carApp = createRhmiApp()
			carApp.loadFromXML(carAppResources.getUiDescription()!!.readBytes())

			mapView = RHMIState.PlainState(carApp, 26)
			carApp.states[mapView.id] = mapView

			val mapTitle = RHMIModel.TextIdModel(carApp, 392)
			carApp.models[mapTitle.id] = mapTitle
			mapView.textModel = mapTitle.id

			mapLabel = RHMIComponent.Label(carApp, 117)
			mapLabel.model = 393
			carApp.components[mapLabel.id] = mapLabel

			mapResults = RHMIModel.RaListModel(carApp, 661)
			carApp.models[mapResults.id] = mapResults

			createMap()

			initWidgets()
			MainViewModel.log("CarApp running")
		} catch (e: Exception) {
			MainViewModel.log("Exception: $e")
			Log.w(TAG, e)
			throw e
		}
	}

	private fun createRhmiApp(): RHMIApplication {
		// create the app in the car
		MainViewModel.log("Creating RHMI")
		val rhmiHandle = carConnection.rhmi_create(null, BMWRemoting.RHMIMetaData("io.bimmergestalt.idriveexperiments.map", BMWRemoting.VersionInfo(0, 1, 0), "io.bimmergestalt.idriveexperiments.richtext", "io.bimmergestalt"))
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.DESCRIPTION, carAppResources.getUiDescription())
		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.TEXTDB, carAppResources.getTextsDB("common"))
//		carConnection.rhmi_setResourceCached(rhmiHandle, BMWRemoting.RHMIResourceType.IMAGEDB, carAppResources.getImagesDB(iDriveConnectionStatus.brand ?: "common"))
		carConnection.rhmi_initialize(rhmiHandle)

		MainViewModel.log("Created RHMI")

		// register for events from the car
		carConnection.rhmi_addActionEventHandler(rhmiHandle, "io.bimmergestalt.idriveexperiments.map", -1)
		carConnection.rhmi_addHmiEventHandler(rhmiHandle, "io.bimmergestalt.idriveexperiments.map", -1, -1)

		return RHMIApplicationSynchronized(
			RHMIApplicationIdempotent(
				RHMIApplicationEtch(carConnection, rhmiHandle)
			), carConnection)
	}

	private fun createMap() {
		MainViewModel.log("map_create")
		mapHandle = carConnection.map_create()
		MainViewModel.log("Created map handle $mapHandle")
	}

	private fun initWidgets() {
		carApp.components.values.filterIsInstance<RHMIComponent.EntryButton>().forEach {
			it.getAction()?.asHMIAction()?.getTargetModel()?.asRaIntModel()?.value = mapView.id
		}
		mapView.getTextModel()?.asTextIdModel()?.textId = 221
		mapLabel.setEnabled(true)
		mapLabel.setVisible(true)
		mapLabel.getModel()?.asTextIdModel()?.textId  = 221
		mapView.visibleCallback = VisibleCallback {
			try {
				if (it) {
					MainViewModel.log("Map screen visible, starting to import")
					showMap()
				} else {
					MainViewModel.log("Map invisible, hiding overlay")
					carConnection.map_hideOverlay(mapHandle, "test.kmz", 1.toShort())
				}
			} catch (e: Exception) {
				MainViewModel.log("Exception in map focus callback: $e")
				Log.w(TAG, e)
			}
		}

		mapResults.value = RHMIModel.RaListModel.RHMIListConcrete(2).apply {
			addRow(arrayOf("", "Result1"))
			addRow(arrayOf("", "Result2"))
			addRow(arrayOf("", "Result3"))
		}
	}

	private fun showMap() {

		MainViewModel.log("Loading KMZ")
		val kmzData = carAppResources.loadFile("kml.kmz")!!.readBytes()

		MainViewModel.log("map_initializeImport")
		carConnection.map_initializeImport(mapHandle, "test.kmz", 1, kmzData.size)
		Thread.sleep(1000)
		MainViewModel.log("map_importData")
		carConnection.map_importData(mapHandle, 1, 0, kmzData)
		Thread.sleep(1000)
		MainViewModel.log("map_finalizeImport")
		carConnection.map_finalizeImport(mapHandle, 1)
		Thread.sleep(1000)
		MainViewModel.log("Finalized import")

		carConnection.map_showOverlay(mapHandle, "test.kmz", 1.toShort())
		MainViewModel.log("Showed overlay")
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
			try {
				// generic event handler
				carApp.states[componentId]?.onHmiEvent(eventId, args)
				carApp.components[componentId]?.onHmiEvent(eventId, args)
			} catch (e: Exception) {
				Log.e(TAG, "Received exception while handling rhmi_onHmiEvent", e)
			}
		}

		override fun map_onEvent(handle: Int?, transferId: Int?, event: BMWRemoting.MapEvent?) {
			MainViewModel.log("Received map event $event")
		}
	}
}