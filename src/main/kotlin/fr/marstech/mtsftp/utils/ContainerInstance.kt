package fr.marstech.mtsftp.utils

import java.time.Duration
import java.time.Instant
import java.util.*

class ContainerInstance(
    val instanceUUID: String = UUID.randomUUID().toString(),
    private val instanceCreationTimeStamp: Instant = Instant.now(),
    var instanceLockTimeStamp: Instant = Instant.now()
) {
    fun isInstanceCreationTimeStampExpired(): Boolean =
        Duration.between(instanceCreationTimeStamp, Instant.now()) > instanceCreationTimeStampTTL

    fun isInstanceLockTimeStampExpired(): Boolean =
        Duration.between(instanceLockTimeStamp, Instant.now()) > instanceLockTimeStampTTL

    companion object {
        val instanceCreationTimeStampTTL: Duration = Duration.ofMinutes(30)
        val instanceLockTimeStampTTL: Duration = Duration.ofMinutes(5)
    }
}