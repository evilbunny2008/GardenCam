package com.vladpen

import android.os.StrictMode
import android.util.Log
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import com.vladpen.cams.MainApp.Companion.context
import java.io.File
import java.io.IOException
import java.lang.Thread.sleep

data class FileDataModel(val name: String, val size: Long, val isDir: Boolean)

class FileData(private val sftpUrl: String?) {

    companion object {
        private var session: Session? = null
        private var channel: ChannelSftp? = null

        fun getParentPath(remotePath: String): String {
            val p = if (remotePath.endsWith("/"))
                remotePath.substring(0, remotePath.length - 1)
            else
                remotePath
            return p.substring(0, p.lastIndexOf("/") + 1)
        }

        fun getTmpFile(file: String): File {
            return File(context.cacheDir.path + "/video." + getExtension(file))
        }

        fun getExtension(file: String): String {
            return file.substring(file.lastIndexOf(".") + 1)
        }
    }

    fun getAll(remotePath: String): MutableList<FileDataModel> {
        val files = mutableListOf<FileDataModel>()

        connect()
        try {
            val ls = channel?.ls(remotePath)
            ls?.forEach {
                val e = it as ChannelSftp.LsEntry
                if (e.filename == "." || e.filename == "..")
                    return@forEach
                val attrs = e.attrs
                val f = FileDataModel(e.filename, attrs.size, attrs.isDir)
                files.add(f)
            }
            files.sortBy { it.name }
        } catch (e: Exception) {
            Log.e("SFTP", "Can't list $remotePath (${e.localizedMessage})")
        } finally {
            disconnect()
        }
        return files
    }

    /**
     * Copy remote video file to local cache.
     *
     * @param path relative SFTP folder's path; starts and ends with "/"
     * @param file file name
     */
    fun remoteToCache(path: String, file: String) {
        val tmpFile = getTmpFile(file)

        try {
            tmpFile.writeText("") // create if not exists and set zero size
        } catch (e: Exception) {
            Log.e("SFTP", "Can't create tmp file (${e.localizedMessage})")
        }
        Thread {
            connect()
            try {
                channel?.get(path + file, tmpFile.absolutePath)
            } catch (e: Exception) {
                Log.e("SFTP", "Can't copy file $path$file (${e.localizedMessage})")
            } finally {
                disconnect()
            }
        }.start()

        var size: Long = 0
        var i = 0
        try {
            while (size == 0L && i++ < 1000) { // wait until downloading will start
                sleep(30)
                size = tmpFile.length()
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
        sleep(300)
    }

    private fun connect() { // suspend
        val sftpData = Utils.parseUrl(sftpUrl, 22, "sftp") ?: return

        val policy = StrictMode.ThreadPolicy.Builder().permitNetwork().build()
        StrictMode.setThreadPolicy(policy)

        try {
            if (channel != null || session != null)
                disconnect()

            val password = Utils.decodeString(sftpData.password)

            val jsch = JSch()
            session = jsch.getSession(sftpData.user, sftpData.host, sftpData.port)
            session?.setPassword(password.toByteArray())
            session?.setConfig("StrictHostKeyChecking", "no")
            session?.connect(3000)

            channel = session?.openChannel("sftp") as ChannelSftp
            channel?.connect(10000)
        } catch (e: Exception) {
            Log.e("SFTP", "Can't connect to ${sftpData.host} (${e.localizedMessage})")
        }
    }

    private fun disconnect() {
        channel?.disconnect()
        session?.disconnect()
        channel = null
        session = null
    }
}