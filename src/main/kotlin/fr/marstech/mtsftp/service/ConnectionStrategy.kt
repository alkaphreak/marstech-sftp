package fr.marstech.mtsftp.service

import com.jcraft.jsch.JSch

interface ConnectionStrategy {
    fun build(knownHostsFilePath: String): JSch = JSch().also { it.setKnownHosts(knownHostsFilePath) }
}
