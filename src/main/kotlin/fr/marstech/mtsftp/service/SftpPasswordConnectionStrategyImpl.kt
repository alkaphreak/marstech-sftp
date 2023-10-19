package fr.marstech.mtsftp.service

import com.jcraft.jsch.Session
import mu.KLogging

class SftpPasswordConnectionStrategyImpl(
    private val username: String,
    private val password: String,
    private val remoteHost: String,
    private val port: Int?,
    private val strictHostChecking: Boolean = true
) : ConnectionStrategy {
    fun connect(knownHostsFilePath: String): Session = build(knownHostsFilePath).run {
        logger.debug("connectWithPassword on host $remoteHost:$port")
        port?.let { getSession(username, remoteHost, it) } ?: getSession(username, remoteHost)
    }.also {
        it.setPassword(password)
        it.setConfig("StrictHostKeyChecking", if (strictHostChecking) "yes" else "no")
        it.setConfig("HashKnownHosts", "yes")
        it.connect()
        logger.debug("session opened on remote host")
    }

    companion object : KLogging()
}
