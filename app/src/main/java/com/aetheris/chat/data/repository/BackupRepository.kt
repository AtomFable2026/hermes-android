package com.aetheris.chat.data.repository

import android.content.Context
import com.aetheris.chat.data.local.dao.CachedModelDao
import com.aetheris.chat.data.local.dao.ConversationDao
import com.aetheris.chat.data.local.dao.CustomProviderDao
import com.aetheris.chat.data.local.dao.MessageDao
import com.aetheris.chat.data.local.entity.CachedModelEntity
import com.aetheris.chat.data.local.entity.ConversationEntity
import com.aetheris.chat.data.local.entity.CustomProviderEntity
import com.aetheris.chat.data.local.entity.MessageEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom

@Serializable
data class BackupData(
    val conversations: List<ConversationEntity>,
    val messages: List<MessageEntity>,
    val providers: List<CustomProviderEntity>,
    val models: List<CachedModelEntity>,
    val timestamp: Long = System.currentTimeMillis()
)

@Singleton
class BackupRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val providerDao: CustomProviderDao,
    private val modelDao: CachedModelDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun exportBackup(password: String, outputFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val data = BackupData(
                conversations = conversationDao.getAllConversationsSync(),
                messages = emptyList(), // Messages will be exported per conversation for scale
                providers = providerDao.getAllProvidersSync(),
                models = modelDao.getAllCachedModelsSync()
            )
            
            // For simplicity in this implementation, we get all messages. 
            // In a real production app with 10k+ messages, we'd stream this.
            val allMessages = mutableListOf<MessageEntity>()
            data.conversations.forEach { conv ->
                allMessages.addAll(messageDao.getMessagesForConversationSync(conv.id))
            }
            
            val fullData = data.copy(messages = allMessages)
            val jsonString = json.encodeToString(fullData)
            val encryptedBytes = encrypt(jsonString.toByteArray(), password)
            
            outputFile.writeBytes(encryptedBytes)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun importBackup(password: String, inputFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val encryptedBytes = inputFile.readBytes()
            val decryptedBytes = decrypt(encryptedBytes, password)
            val jsonString = String(decryptedBytes)
            val data = json.decodeFromString<BackupData>(jsonString)

            // Import data
            data.providers.forEach { providerDao.insertProvider(it) }
            data.models.forEach { modelDao.insertModel(it) }
            data.conversations.forEach { conversationDao.insertConversation(it) }
            data.messages.forEach { messageDao.insertMessage(it) }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun encrypt(data: ByteArray, password: String): ByteArray {
        val salt = ByteArray(16).apply { SecureRandom().nextBytes(this) }
        val iv = ByteArray(12).apply { SecureRandom().nextBytes(this) }
        
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        
        val ciphertext = cipher.doFinal(data)
        return salt + iv + ciphertext
    }

    private fun decrypt(data: ByteArray, password: String): ByteArray {
        val salt = data.sliceArray(0 until 16)
        val iv = data.sliceArray(16 until 28)
        val ciphertext = data.sliceArray(28 until data.size)
        
        val key = deriveKey(password, salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        
        return cipher.doFinal(ciphertext)
    }

    private fun deriveKey(password: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, 65536, 256)
        val tmp = factory.generateSecret(spec)
        return SecretKeySpec(tmp.encoded, "AES")
    }
}
