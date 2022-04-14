package io.bimmergestalt.idriveexperiments.richtext

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel: ViewModel() {
	companion object {
		val dataLiveData = MutableLiveData("Initial text\n<info>Info text</>\n<color=33ff66ff>Color text</>\n")
	}

	val dataLiveData = MainViewModel.dataLiveData
}