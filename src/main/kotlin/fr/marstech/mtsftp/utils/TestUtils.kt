package fr.marstech.mtsftp.utils

import org.slf4j.Logger
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.images.PullPolicy
import org.testcontainers.utility.LogUtils
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.*

fun createInputTempFile(): Path =
    Files.createTempFile(
        Files.createTempDirectory("files-in"),
        "file-in",
        null
    )

fun createOutputTempDirectory(): Path =
    Files.createTempDirectory("files-out")

fun makeTmpFs(folderName: String): MutableMap<String, String> =
    Collections.singletonMap(
        Files.createTempDirectory(folderName).toString(),
        "rw"
    )

fun getReuseLabel(): String = "reuse.UUID"

fun GenericContainer<*>.startOrReuseUniqueInstance(
    instanceUUID: String? = null,
    tmpFolderName: String? = null,
    env: Map<String, String> = emptyMap(),
    vararg exposedPorts: Int,
    logger: Logger? = null
): GenericContainer<*> {
    // Container can have tmpfs mounts for storing data in host memory
    // useful to speed up database tests.
    withTmpFs(makeTmpFs(tmpFolderName ?: instanceUUID!!))
    env.forEach { addEnv(it.key, it.value) }
    if (exposedPorts.isNotEmpty()) withExposedPorts(*exposedPorts.toTypedArray())
    withImagePullPolicy(PullPolicy.ageBased(Duration.ofDays(30)))
    withStartupAttempts(1)
    withReuse(true)
    withLabel(getReuseLabel(), instanceUUID)
    start()
    if (logger != null) {
        LogUtils.followOutput(
            this.dockerClient,
            this.containerId,
            Slf4jLogConsumer(logger).withSeparateOutputStreams()
        )
    }
    return this
}
