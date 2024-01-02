package fr.marstech.mtsftp.utils

import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import org.testcontainers.containers.GenericContainer
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.time.Instant

private val logger = KotlinLogging.logger {}

class RedisContainer(
    dockerImageName: String = IMAGE,
) : GenericContainer<RedisContainer>(dockerImageName) {

    private val jedisPool: JedisPool by lazy {
        JedisPool(JedisPoolConfig(), this.host, this.getMappedPort(PORT))
    }

    fun getFreeInstanceUUID(type: String, mapper: ObjectMapper): String {
        jedisPool.resource.use { jedis ->
            return when {
                hasFreeInstance(jedis, type) -> popFree(jedis, type, mapper).apply {
                    instanceLockTimeStamp = Instant.now()
                    pushLock(jedis, type, mapper, this)
                }.instanceUUID

                else -> ContainerInstance().apply {
                    pushLock(jedis, type, mapper, this)
                }.instanceUUID
            }
        }
    }

    fun freeInstance(type: String, mapper: ObjectMapper, instanceUUID: String) {
        jedisPool.resource.use { jedis ->
            repeat(lockLength(jedis, type).toInt()) {
                popLock(jedis, type, mapper).also {
                    if (it.instanceUUID == instanceUUID) {
                        pushFree(jedis, type, mapper, it)
                    } else {
                        pushLock(jedis, type, mapper, it)
                    }
                }
            }
        }
    }

    fun freeInstances(type: String, mapper: ObjectMapper) {
        jedisPool.resource.use { jedis ->
            repeat(lockLength(jedis, type).toInt()) {
                popLock(jedis, type, mapper).also {
                    when {
                        it.isInstanceLockTimeStampExpired() -> pushFree(jedis, type, mapper, it)
                        it.isInstanceCreationTimeStampExpired() -> killInstance(it)
                        else -> pushLock(jedis, type, mapper, it)
                    }
                }
            }
        }
    }

    private fun killInstance(containerInstance: ContainerInstance) {
        killInstance(containerInstance.instanceUUID)
    }

    fun killInstance(instanceUUID: String) {
        DockerUtils.getContainerFromLabel(getReuseLabel(), instanceUUID)?.id?.let { containerId ->
            DockerUtils.stopContainer(containerId)
            DockerUtils.killContainer(containerId)
        }
    }

    private fun popLock(
        jedis: Jedis,
        type: String,
        mapper: ObjectMapper
    ): ContainerInstance = mapper.readValue(
        jedis.lpop(typeLock(type)),
        ContainerInstance::class.java
    )

    private fun pushLock(
        jedis: Jedis,
        type: String,
        mapper: ObjectMapper,
        instance: ContainerInstance
    ) {
        jedis.rpush(typeLock(type), mapper.writeValueAsString(instance))
        logger.info { "Locking ${instance.instanceUUID}" }
    }

    private fun popFree(
        jedis: Jedis,
        type: String,
        mapper: ObjectMapper
    ): ContainerInstance = mapper.readValue(
        jedis.lpop(typeFree(type)),
        ContainerInstance::class.java
    )

    private fun pushFree(
        jedis: Jedis,
        type: String,
        mapper: ObjectMapper,
        instance: ContainerInstance
    ) {
        jedis.rpush(typeFree(type), mapper.writeValueAsString(instance))
        logger.info { "Releasing ${instance.instanceUUID}" }
    }

    private fun hasFreeInstance(jedis: Jedis, type: String): Boolean = freeLength(jedis, type) > 0

    private fun lockLength(jedis: Jedis, type: String) = jedis.llen(typeLock(type))

    private fun freeLength(jedis: Jedis, type: String) = jedis.llen(typeFree(type))

    private fun typeLock(type: String) = "$type-lock"

    private fun typeFree(type: String) = "$type-free"

    companion object {
        const val INSTANCE_UUID: String = "ad4ad799-ae9e-453e-8deb-8b777ec521d3"
        const val IMAGE: String = "redis:alpine"
        const val PORT: Int = 6379
    }
}