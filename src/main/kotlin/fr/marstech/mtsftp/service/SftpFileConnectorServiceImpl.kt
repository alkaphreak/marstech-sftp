package fr.marstech.mtsftp.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class SftpFileConnectorServiceImpl(
    @Value("\${mtsftp.knownHosts:''}")
    var knownHostsFilePath: String
) : FileConnectorService {

    override fun connect(strategy: ConnectionStrategy): Connection = when (strategy) {
        is SftpPasswordConnectionStrategyImpl -> SftpConnectionImpl(strategy.connect(knownHostsFilePath))
        is SftpPrivateKeyConnectionStrategyImpl -> SftpConnectionImpl(strategy.connect(knownHostsFilePath))
        else -> throw UnsupportedOperationException("Unsupported connection strategy: $strategy")
    }
}
