package io.bimmergestalt.idriveexperiments.map


import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MainViewModel: ViewModel() {
	companion object {
		val handler = Handler(Looper.getMainLooper())
		val dataLiveData = MutableLiveData("Starting")

		fun log(message: String) {
			Log.i(TAG, message)
			handler.post {
				dataLiveData.value = dataLiveData.value + "\n" + message
			}
		}
	}

	val dataLiveData = MainViewModel.dataLiveData
}