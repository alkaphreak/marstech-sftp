package fr.marstech.mtsftp.service

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.pathString

private val logger = KotlinLogging.logger {}

class SftpConnectionImpl(private val session: Session) : Connection {

    private val channelSftp: ChannelSftp by lazy {
        (session.openChannel("sftp") as ChannelSftp).apply { connect() }
    }

    override fun uploadFile(localFilePath: Path, remoteFilePath: Path) {
        logger.debug { "upload file from $localFilePath to $remoteFilePath" }
        channelSftp.put(localFilePath.pathString, remoteFilePath.pathString)
        logger.debug { "file uploaded" }
    }

    override fun downloadFile(remoteFilePath: Path, localFilePath: Path) {
        logger.debug { "download file from $remoteFilePath to $localFilePath" }
        channelSftp.get(remoteFilePath.pathString, localFilePath.pathString)
        logger.debug { "file downloaded" }
    }

    override fun listRemoteFiles(remoteDirectoryPath: Path): Set<Path> {
        logger.debug { "listing files from $remoteDirectoryPath" }
        return channelSftp.ls(remoteDirectoryPath.pathString).asSequence()
            .filterIsInstance<ChannelSftp.LsEntry>()
            .map { entry -> remoteDirectoryPath.resolve(entry.filename) }
            .toSet()
            .also { logger.debug { "end file list. Found ${it.size} files" } }
    }

    override fun disconnect() = try {
        channelSftp.exit()
    } finally {
        session.disconnect()
    }
}
