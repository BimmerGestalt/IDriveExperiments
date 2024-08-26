package io.bimmergestalt.idriveconnectkit.android.security

import org.junit.Test

import org.junit.Assert.*

class PrivateKeyHandlingTest {

	val TOKEN = "AAABAAsASgB+AF4AeQAmADEAJAAgAGoAaQByAGwAJAArAAcAUQAkAEcAEAA3AG8A" +
			"JwAFAFoAVgBpAGIAZgBhAGYAbQBlAHoALABNAEQAMAA/ACYAbgBGAFQASABfAEAA" +
			"RgAKADAAdAApAHAAOgBWAF8AfwB5AGkAcAAEAFAAYwBaAGAARgBFAGMAbwArAE0A" +
			"OgBeAEcAAgA0ACIAKwBeAEAAYgA="

	@Test
	fun decodePassphrase() {
		val decoded = PrivateKeyHandling.decodePassphrase(TOKEN, "de.bmw.a4a.BMWAppKit")
		assertEquals(40, decoded.length)
		assertTrue(decoded.startsWith("sw6+xm:ZG"))
	}

	@Test
	fun decodePassphraseFail() {
		assertThrows(IllegalArgumentException::class.java) {
			PrivateKeyHandling.decodePassphrase(TOKEN, "de.bmw.a4a")
		}
	}

	@Test
	fun loadPrivateKey() {
		val pkcs12 = this.javaClass.classLoader!!.getResourceAsStream("BMWAppKitDevelopment.p12")
		val passphrase = PrivateKeyHandling.decodePassphrase(TOKEN, "de.bmw.a4a.BMWAppKit")
		val key = PrivateKeyHandling.loadPrivateKey(pkcs12, passphrase)
		assertNotNull(key)
	}

	@Test
	fun loadPrivateKeyFail() {
		val pkcs12 = this.javaClass.classLoader!!.getResourceAsStream("BMWAppKitDevelopment.p12")
		val passphrase = PrivateKeyHandling.decodePassphrase(TOKEN, "de.bmw.a4a.BMWAppKit")
		assertThrows(IllegalArgumentException::class.java) {
			PrivateKeyHandling.loadPrivateKey(pkcs12, passphrase.substring(0..<10))
		}
	}

	@OptIn(ExperimentalStdlibApi::class)
	@Test
	fun signChallenge() {
		// the pkcs12 from BMW Connected 10.4 for iOS
		val pkcs12 = this.javaClass.classLoader!!.getResourceAsStream("BMWAppKitDevelopment.p12")
		val passphrase = PrivateKeyHandling.decodePassphrase(TOKEN, "de.bmw.a4a.BMWAppKit")
		val key = PrivateKeyHandling.loadPrivateKey(pkcs12, passphrase)!!

		val challenge = byteArrayOf(
			0x6d, 0x58, 0x5f, 0x14,
			0x72, 0x72, 0x19, 0x75,
			0x4e, 0x73, 0x19, 0x38,
			0x61, 0x2f, 0x50, 0x78)
		val response = PrivateKeyHandling.signChallenge(challenge, key, true)
		assertEquals(192, response.size)
		print(response.toHexString())
		assertEquals(0x4d.toByte(), response[0])
		assertEquals(0x0e.toByte(), response[1])
		assertEquals(0x33.toByte(), response[2])

		val response2 = PrivateKeyHandling.signChallenge(challenge, key, false)
		assertEquals(192, response.size)
		print(response.toHexString())
		assertEquals(0xc2.toByte(), response2[0])
		assertEquals(0x59.toByte(), response2[1])
		assertEquals(0xc5.toByte(), response2[2])
	}
}