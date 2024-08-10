package io.bimmergestalt.idriveexperiments.certs

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.text.method.ScrollingMovementMethod
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity


class MainActivity : AppCompatActivity() {
	lateinit var boundService: ServiceConnection

	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		val binding = MainBinding.inflate(layoutInflater, findViewById(android.R.id.content), false)
		binding.lifecycleOwner = this
		binding.viewModel = MainViewModel()
		setContentView(binding.root)

		binding.root.findViewById<TextView>(R.id.txtView).movementMethod = ScrollingMovementMethod()

		startCarService()
	}

	fun startCarService() {
		val intent = Intent(CarAppService.ACTION_UICONNECTED)
			.putExtra(CarAppService.EXTRA_UICONNECTED, true)
			.setClass(this, CarAppService::class.java)
		val conn = CarServiceConnection()
		bindService(intent, conn, Context.BIND_AUTO_CREATE)
		boundService = conn
	}

	override fun onDestroy() {
		super.onDestroy()
		unbindService(boundService)
	}


	class CarServiceConnection(): ServiceConnection {
		var connected = false
			private set

		override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
			connected = true
		}
		override fun onNullBinding(name: ComponentName?) {
			connected = true
		}
		override fun onServiceDisconnected(name: ComponentName?) {
			connected = false
		}
	}
}