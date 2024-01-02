package fr.marstech.mtsftp.utils

import io.github.oshai.kotlinlogging.DelegatingKLogger
import io.github.oshai.kotlinlogging.KLogger
import org.slf4j.Logger
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.images.PullPolicy
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

fun createInputTempFile(): Path =
    Files.createTempFile("file-in", ".tmp")

fun createOutputTempDirectory(): Path =
    Files.createTempDirectory("files-out")

fun makeTmpFs(folderName: String): Map<String, String> =
    mapOf(Files.createTempDirectory(folderName).toString() to "rw")

fun getReuseLabel(): String = "reuse.UUID"

fun GenericContainer<*>.startOrReuseUniqueInstance(
    instanceUUID: String,
    tmpFolderName: String = instanceUUID,
    env: Map<String, String> = emptyMap(),
    vararg exposedPorts: Int,
    logger: Logger? = null
): GenericContainer<*> {
    withTmpFs(makeTmpFs(tmpFolderName))
    env.forEach { addEnv(it.key, it.value) }
    if (exposedPorts.isNotEmpty()) withExposedPorts(*exposedPorts.toTypedArray())
    withImagePullPolicy(PullPolicy.ageBased(Duration.ofDays(30)))
    withStartupAttempts(1)
    withReuse(true)
    withLabel(getReuseLabel(), instanceUUID)
    start()
    logger?.let {
        followOutput(Slf4jLogConsumer(it).withSeparateOutputStreams())
    }
    return this
}

// Extension function to safely convert KLogger to org.slf4j.Logger using reflection
@Suppress("UNCHECKED_CAST")
fun KLogger.toSlf4j(): Logger = (this as DelegatingKLogger<Logger>).underlyingLogger