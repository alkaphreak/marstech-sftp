package fr.marstech.mtsftp.service

import com.jcraft.jsch.Session
import mu.KLogging
import java.nio.file.Path
import kotlin.io.path.pathString

class SftpPrivateKeyConnectionStrategyImpl(
    private val username: String,
    private val privateKey: Path,
    private val remoteHost: String,
    private val port: Int?,
    private val strictHostChecking: Boolean = true
) : ConnectionStrategy {
    fun connect(knownHostsFilePath: String): Session = build(knownHostsFilePath)
        .apply { addIdentity(privateKey.pathString) }
        .run {
            logger.debug("connectWithPrivateKey on host $remoteHost:$port")
            port?.let { getSession(username, remoteHost, it) } ?: getSession(username, remoteHost)
        }.also {
            it.setConfig("StrictHostKeyChecking", if (strictHostChecking) "yes" else "no")
            it.setConfig("HashKnownHosts", "yes")
            it.connect()
            logger.debug("session opened on remote host")
        }

    companion object : KLogging()
}
