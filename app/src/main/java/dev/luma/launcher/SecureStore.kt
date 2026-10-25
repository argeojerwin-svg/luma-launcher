package dev.luma.launcher

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Profile-scoped Android Keystore encryption for Trakt tokens and private cached history. */
class SecureStore(private val context:Context, private val profile:Int) {
    private val data=context.getSharedPreferences("luma_secure",Context.MODE_PRIVATE)
    private val alias="luma_trakt_profile_$profile"
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(alias,null) as? SecretKey)?.let{return it}
        val generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore")
        generator.init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        return generator.generateKey()
    }
    fun put(name:String,value:String){
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key())
        val bytes=cipher.iv+cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        data.edit().putString("p${profile}_$name",Base64.encodeToString(bytes,Base64.NO_WRAP)).apply()
    }
    fun get(name:String):String?=runCatching{
        val bytes=Base64.decode(data.getString("p${profile}_$name",null)?:return null,Base64.NO_WRAP)
        val cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,bytes.copyOfRange(0,12)))
        String(cipher.doFinal(bytes.copyOfRange(12,bytes.size)),Charsets.UTF_8)
    }.getOrNull()
    fun clear(name:String){data.edit().remove("p${profile}_$name").apply()}
}
