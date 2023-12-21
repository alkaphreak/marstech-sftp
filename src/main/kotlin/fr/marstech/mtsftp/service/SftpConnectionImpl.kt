package fr.marstech.mtsftp.service

import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.pathString

private val logger = KotlinLogging.logger {}

class SftpConnectionImpl(private val session: Session) : Connection {

    private fun channelSftp(): ChannelSftp =
        session.openChannel("sftp")
            .apply { connect() } as ChannelSftp

    override fun uploadFile(localFilePath: Path, remoteFilePath: Path) {
        channelSftp().apply {
            logger.debug { "${"upload file from {} to {}"} $localFilePath $remoteFilePath" }
            put(localFilePath.pathString, remoteFilePath.pathString)
            exit()
            logger.debug { "file uploaded" }
        }
    }

    override fun downloadFile(remoteFilePath: Path, localFilePath: Path) {
        channelSftp().apply {
            logger.debug { "${"download file from {} to {}"} $remoteFilePath $localFilePath" }
            get(remoteFilePath.pathString, localFilePath.pathString)
            exit()
            logger.debug { "file downloaded" }
        }
    }

    override fun listRemoteFiles(remoteDirectoryPath: Path): Set<Path> = (
            channelSftp().apply {
                logger.debug { "${"listing files from {}"} $remoteDirectoryPath" }
                cd(remoteDirectoryPath.pathString)
            }.ls(remoteDirectoryPath.pathString).stream() as Stream<ChannelSftp.LsEntry>
            ).map { entry -> remoteDirectoryPath.resolve(entry.filename) }
        .toList()
        .toSet()
        .apply<Set<Path>> { logger.debug { "end file list. Found $size files" } }

    override fun disconnect() = session.disconnect()
}
