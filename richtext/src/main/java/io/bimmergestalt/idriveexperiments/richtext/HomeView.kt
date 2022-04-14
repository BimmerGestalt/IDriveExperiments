package io.bimmergestalt.idriveexperiments.richtext

import androidx.lifecycle.asFlow
import io.bimmergestalt.idriveconnectkit.rhmi.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.debounce

class HomeView(val state: RHMIState) {
	companion object {
		fun fits(state: RHMIState): Boolean {
			return state.componentsList.filterIsInstance<RHMIComponent.List>().any {
				it.getModel()?.modelType == "Richtext"
			}
		}
	}

	private val coroutineScope = CoroutineScope(Job() + Dispatchers.IO)
	private val widget = state.componentsList.filterIsInstance<RHMIComponent.List>().first {
		it.getModel()?.modelType == "Richtext"
	}
	private val richtextModel = widget.getModel()!!

	fun initWidgets() {
		state.setProperty(RHMIProperty.PropertyId.HMISTATE_TABLETYPE, 3)
		state.componentsList.forEach {
			it.setVisible(false)
		}
		widget.apply {
			setVisible(true)
			setEnabled(true)
			setSelectable(true)
		}
		state.focusCallback = FocusCallback { focused ->
			if (focused) {
				show()
			} else {
				coroutineScope.coroutineContext.cancelChildren()
			}
		}
		show()
	}

	private fun show() {
		coroutineScope.coroutineContext.cancelChildren()
		coroutineScope.launch {
			// consume a flow
			MainViewModel.dataLiveData.asFlow().debounce(300).collect {
				val listData = RHMIModel.RaListModel.RHMIListConcrete(1)
				listData.addRow(arrayOf("$it      <info><>"))
				richtextModel.value = listData
			}
		}
	}
}