package com.karelisio.ruban.data.backup

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.DataInputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class WrongPasswordException : Exception("Mot de passe incorrect ou fichier abîmé")

/**
 * Password based AES-256-GCM for backup files.
 * Layout: "RUBAN" + version(1) + salt(16) + iv(12) + ciphertext+tag.
 */
object BackupCrypto {
    private val MAGIC = "RUBAN".toByteArray()
    private const val VERSION: Byte = 1
    private const val ITERATIONS = 210_000
    private val random = SecureRandom()

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(bytes, "AES")
    }

    /** Wraps [out]: everything written to the returned stream is encrypted. Close it to write the tag. */
    fun encrypt(out: OutputStream, password: CharArray): OutputStream {
        val salt = ByteArray(16).also(random::nextBytes)
        val iv = ByteArray(12).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
            updateAAD(MAGIC)
        }
        out.write(MAGIC)
        out.write(byteArrayOf(VERSION))
        out.write(salt)
        out.write(iv)
        return CipherOutputStream(out, cipher)
    }

    /** Fully decrypts [input] (GCM authenticates the whole file before releasing data). */
    fun decrypt(input: InputStream, password: CharArray): ByteArray {
        val data = DataInputStream(input)
        val magic = ByteArray(MAGIC.size).also(data::readFully)
        if (!magic.contentEquals(MAGIC)) throw IllegalArgumentException("Ce fichier n'est pas une sauvegarde Ruban")
        val version = data.readByte()
        if (version != VERSION) throw IllegalArgumentException("Version de sauvegarde inconnue")
        val salt = ByteArray(16).also(data::readFully)
        val iv = ByteArray(12).also(data::readFully)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(128, iv))
            updateAAD(MAGIC)
        }
        return try {
            cipher.doFinal(data.readBytes())
        } catch (e: javax.crypto.AEADBadTagException) {
            throw WrongPasswordException()
        }
    }
}

/** Encrypts small secrets (the auto-backup password) with a non-exportable Android Keystore key. */
object SecretBox {
    private const val ALIAS = "ruban-secret-box"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    fun seal(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val sealed = cipher.iv + cipher.doFinal(plain.toByteArray())
        return Base64.encodeToString(sealed, Base64.NO_WRAP)
    }

    fun open(sealed: String): String? = runCatching {
        val bytes = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
        }
        String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)))
    }.getOrNull()
}
