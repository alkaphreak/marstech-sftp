package fr.marstech.mtsftp.service

interface FileConnectorService {
    fun connect(strategy: ConnectionStrategy): Connection
}
