package com.goldsonhwy.yellowplayer.smb

import android.content.Context
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import com.goldsonhwy.yellowplayer.data.local.db.AppDatabase
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import java.io.IOException
import java.io.InputStream
import java.util.Properties

class SmbStreamDataSource(private val appContext: Context) : BaseDataSource(true) {
    private var inputStream: InputStream? = null
    private var smbFile: SmbFile? = null
    private var bytesRemaining: Long = C.LENGTH_UNSET.toLong()
    private var opened = false
    private var currentUri: Uri? = null

    override fun open(dataSpec: DataSpec): Long {
        currentUri = dataSpec.uri
        transferInitializing(dataSpec)
        try {
            val uri = dataSpec.uri
            val url = uri.toString()
            val server = findServerForUri(uri)
                ?: throw IOException("SMB server config not found for ${uri.host}")
            val ctx = createContext(server.username, server.password)
            val file = SmbFile(url, ctx)
            smbFile = file
            val length = file.length()
            val start = dataSpec.position
            val requested = dataSpec.length
            val stream = file.inputStream
            if (start > 0) stream.skip(start)
            inputStream = stream
            bytesRemaining = if (requested != C.LENGTH_UNSET.toLong()) requested else length - start
            opened = true
            transferStarted(dataSpec)
            return bytesRemaining
        } catch (e: Exception) {
            throw IOException("Open SMB stream failed: ${e.message}", e)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (bytesRemaining == 0L) return C.RESULT_END_OF_INPUT
        val max = if (bytesRemaining == C.LENGTH_UNSET.toLong()) length else minOf(length.toLong(), bytesRemaining).toInt()
        val read = inputStream?.read(buffer, offset, max) ?: return C.RESULT_END_OF_INPUT
        if (read == -1) return C.RESULT_END_OF_INPUT
        if (bytesRemaining != C.LENGTH_UNSET.toLong()) bytesRemaining -= read.toLong()
        bytesTransferred(read)
        return read
    }

    override fun getUri(): Uri? = currentUri

    override fun close() {
        currentUri = null
        try { inputStream?.close() } finally {
            inputStream = null
            smbFile = null
            if (opened) {
                opened = false
                transferEnded()
            }
        }
    }

    private fun findServerForUri(uri: Uri): com.goldsonhwy.yellowplayer.data.local.db.SambaServerEntity? {
        val host = uri.host ?: return null
        val db = AppDatabase.getInstance(appContext)
        val all = db.sambaServerDao().getAllServersBlocking()
        return all.firstOrNull { it.host == host }
    }

    private fun createContext(username: String, password: String): CIFSContext {
        val auth = if (username.contains("\\")) {
            NtlmPasswordAuthenticator(username.substringBefore("\\"), username.substringAfter("\\"), password)
        } else {
            NtlmPasswordAuthenticator(null, username, password)
        }
        val props = Properties().apply {
            setProperty("jcifs.smb.client.enableSMB2", "true")
            setProperty("jcifs.smb.client.disableSMB1", "true")
            setProperty("jcifs.smb.client.responseTimeout", "12000")
            setProperty("jcifs.smb.client.soTimeout", "12000")
        }
        return BaseContext(PropertyConfiguration(props)).withCredentials(auth)
    }

    class Factory(private val context: Context) : DataSource.Factory {
        private val defaultFactory = DefaultDataSource.Factory(context)
        private var listener: TransferListener? = null
        override fun createDataSource(): DataSource {
            val smb = SmbStreamDataSource(context.applicationContext)
            listener?.let { smb.addTransferListener(it) }
            val fallback = defaultFactory.createDataSource()
            return object : DataSource {
                private var active: DataSource? = null
                override fun addTransferListener(transferListener: TransferListener) {
                    listener = transferListener
                    smb.addTransferListener(transferListener)
                    fallback.addTransferListener(transferListener)
                }
                override fun open(dataSpec: DataSpec): Long {
                    active = if (dataSpec.uri.scheme == "smb") smb else fallback
                    return active!!.open(dataSpec)
                }
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active!!.read(buffer, offset, length)
                override fun getUri(): Uri? = active?.uri
                override fun close() { active?.close(); active = null }
            }
        }
    }
}
