package fr.marstech.mtsftp.utils

import com.fasterxml.jackson.databind.ObjectMapper
import mu.KLogging
import org.testcontainers.containers.GenericContainer
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.JedisPoolConfig
import java.time.Instant

class RedisContainer(
    dockerImageName: String = IMAGE,
) : GenericContainer<RedisContainer>(dockerImageName) {

    private fun getPool(): JedisPool {
        return JedisPool(
            JedisPoolConfig(),
            this.host,
            this.getMappedPort(PORT)
        )
    }

    private fun closePool() = getPool().close()

    private fun getResource(): Jedis = getPool().resource

    fun getFreeInstanceUUID(type: String, mapper: ObjectMapper): String {
        val instanceUUID: String
        getResource().use { jedis: Jedis ->
            when {
                hasFreeInstance(jedis, type) -> instanceUUID =
                    popFree(jedis, type, mapper).also { instance: ContainerInstance ->
                        instance.instanceLockTimeStamp = Instant.now()
                        pushLock(jedis, type, mapper, instance)
                    }.instanceUUID

                else -> instanceUUID = ContainerInstance().also { instance: ContainerInstance ->
                    pushLock(jedis, type, mapper, instance)
                }.instanceUUID
            }
        }
        closePool()
        return instanceUUID
    }

    fun freeInstance(type: String, mapper: ObjectMapper, instanceUUID: String) {
        logger.info { "Releasing instance : $instanceUUID" }
        getResource().use { jedis ->
            for (i in 1..lockLength(jedis, type)) {
                popLock(jedis, type, mapper).also {
                    when (it.instanceUUID) {
                        instanceUUID -> pushFree(jedis, type, mapper, it)
                        else -> pushLock(jedis, type, mapper, it)
                    }
                }
            }
        }
        closePool()
    }

    fun freeInstances(type: String, mapper: ObjectMapper) {
        logger.info { "Attempt to release instance" }
        getResource().use { jedis ->
            for (i in 1..lockLength(jedis, type)) {
                popLock(jedis, type, mapper).also {
                    when {
                        it.isInstanceLockTimeStampExpired() -> pushFree(jedis, type, mapper, it)
                        it.isInstanceCreationTimeStampExpired() -> killInstance(it)
                        else -> pushLock(jedis, type, mapper, it)
                    }
                }
            }
        }
        closePool()
    }

    private fun killInstance(containerInstance: ContainerInstance) {
        logger.info { "Should kill instance: $containerInstance" }
        TODO("Not yet implemented")
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

    companion object : KLogging() {
        const val INSTANCE_UUID: String = "ad4ad799-ae9e-453e-8deb-8b777ec521d3"
        const val IMAGE: String = "redis:alpine"
        const val PORT: Int = 6379
    }
}