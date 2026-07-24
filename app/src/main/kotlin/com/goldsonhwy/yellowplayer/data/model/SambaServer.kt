package com.goldsonhwy.yellowplayer.data.model

import android.net.Uri

data class SambaServer(
    val id: Long = 0,
    val name: String = "",
    val host: String,
    val port: Int = 445,
    val shareName: String = "",
    val username: String = "",
    val password: String = "",
    val displayName: String = ""
) {
    val uri: Uri get() {
        val creds = if (username.isNotEmpty()) "${username}:${password}@" else ""
        return Uri.parse("smb://${creds}${host}:${port}/${shareName}")
    }
}
