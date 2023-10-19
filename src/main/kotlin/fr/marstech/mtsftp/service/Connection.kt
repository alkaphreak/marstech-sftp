package fr.marstech.mtsftp.service

import java.nio.file.Path

interface Connection {

    fun disconnect()
    fun downloadFile(remoteFilePath: Path, localFilePath: Path)
    fun listRemoteFiles(remoteDirectoryPath: Path): Set<Path>
    fun uploadFile(localFilePath: Path, remoteFilePath: Path)
}
