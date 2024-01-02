package fr.marstech.mtsftp.utils

import java.time.Duration
import java.time.Instant
import java.util.*

class ContainerInstance(
    val instanceUUID: String = UUID.randomUUID().toString(),
    private val instanceCreationTimeStamp: Instant = Instant.now(),
    var instanceLockTimeStamp: Instant = Instant.now()
) {
    fun isInstanceCreationTimeStampExpired(): Boolean {
        val now = Instant.now()
        return Duration.between(instanceCreationTimeStamp, now) > instanceCreationTimeStampTTL
    }

    fun isInstanceLockTimeStampExpired(): Boolean {
        val now = Instant.now()
        return Duration.between(instanceLockTimeStamp, now) > instanceLockTimeStampTTL
    }

    companion object {
        private val instanceCreationTimeStampTTL: Duration = Duration.ofMinutes(30)
        private val instanceLockTimeStampTTL: Duration = Duration.ofMinutes(5)
    }
}
