package io.bimmergestalt.idriveconnectkit.android.security

import java.io.IOException
import java.io.InputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Signature
import java.security.UnrecoverableKeyException
import java.util.Base64
import kotlin.experimental.xor
import kotlin.jvm.Throws

object PrivateKeyHandling {
	/**
	 * Given a token and a package name from a BMW certificate
	 * calculate the passphrase for the private pkcs12 file
	 * Throws IllegalArgumentException for errors decoding
	 */
	@Throws(IllegalArgumentException::class)
	fun decodePassphrase(token: String, name: String): String {
		val tokenArray = Base64.getDecoder().decode(token)
		val decoded = StringBuffer(tokenArray.size / 4)

		if (tokenArray.size < 5) {
			throw IllegalArgumentException("Token is too small")
		}

		var newChar = tokenArray[0]
		for (i in 4 ..< tokenArray.size-3 step 4) {
			if (tokenArray[i+1] != 0.toByte()) {
				throw IllegalArgumentException("High byte found at ${i+1}")
			}
			if (tokenArray[i+3] != 0.toByte()) {
				throw IllegalArgumentException("High byte found at ${i+3}")
			}
			val nameIndex = newChar xor tokenArray[2] xor tokenArray[i]
			if (nameIndex < 0 || nameIndex >= name.length) {
				throw IllegalArgumentException("Out of bounds $nameIndex when decoding token at $i")
			}

			newChar = newChar xor tokenArray[i+2] xor 0x17 xor name[nameIndex.toInt()].code.toByte()
			decoded.append(newChar.toInt().toChar())
		}

		return decoded.toString()
	}

	/**
	 * Opens the given pkcs12 file, unlocking with the given passphrase
	 * Returns null if no private key was found
	 * Throws IllegalArgumentException for invalid passphrase
	 */
	@Throws(IllegalArgumentException::class)
	fun loadPrivateKey(pkcs12: InputStream, passphrase: String): PrivateKey? {
		val password = passphrase.toCharArray()
		val keystore = KeyStore.getInstance("PKCS12")
		try {
			keystore.load(pkcs12, password)
		} catch (e: IOException) {
			throw IllegalArgumentException("Incorrect keystore password", e)
		}
		for (entryName in keystore.aliases()) {
			val key = try {
				keystore.getKey(entryName, password)
			} catch (e: UnrecoverableKeyException) {
				throw IllegalArgumentException("Incorrect key password", e)
			}
			if (key is PrivateKey) {
				return key
			}
		}
		return null
	}

	@OptIn(ExperimentalStdlibApi::class)
	fun signChallenge(challenge: ByteArray, key: PrivateKey, applyXor: Boolean): ByteArray {
		val hasher = MessageDigest.getInstance("MD5")
		hasher.update(byteArrayOf(0x02, 0x00, 0x00, 0x00), 0, 4)
		val halfway = hasher.digest()
		val output = ByteArray(challenge.size)
		challenge.forEachIndexed { index, byte ->
			output[index] = challenge[index] xor halfway[index % halfway.size]
		}
		println(output.toHexString())

		val sign = Signature.getInstance("MD5withRSA")
		sign.initSign(key)
		sign.update(if (applyXor) output else challenge)
		return sign.sign()
	}
}