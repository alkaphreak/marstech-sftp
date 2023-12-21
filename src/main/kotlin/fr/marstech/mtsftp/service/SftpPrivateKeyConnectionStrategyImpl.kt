package fr.marstech.mtsftp.service

import com.jcraft.jsch.Session
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Path
import kotlin.io.path.pathString

private val logger = KotlinLogging.logger {}

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
            logger.debug { "connectWithPrivateKey on host $remoteHost:$port" }
            port?.let { getSession(username, remoteHost, it) } ?: getSession(username, remoteHost)
        }.also {
            it.setConfig("StrictHostKeyChecking", if (strictHostChecking) "yes" else "no")
            it.setConfig("HashKnownHosts", "yes")
            it.connect()
            logger.debug { "session opened on remote host" }
        }
}
