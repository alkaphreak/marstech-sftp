package fr.marstech.mtsftp.utils

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.netty.NettyDockerCmdExecFactory
import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

object DockerUtils {

    private val dockerClient: DockerClient by lazy {
        DockerClientBuilder
            .getInstance()
            .withDockerCmdExecFactory(NettyDockerCmdExecFactory())
            .build().also {
                logger.info { "Docker client initialized" }
            }
    }

    fun getContainerListFromLabel(labelKey: String): List<Container> =
        dockerClient.listContainersCmd()
            .withShowAll(false)
            .exec()
            .filter { it.labels.containsKey(labelKey) }

    fun getContainerFromLabel(labelKey: String, labelValue: String): Container? =
        getContainerListFromLabel(labelKey)
            .firstOrNull { it.labels[labelKey] == labelValue }
            .also {
                if (it != null) logger.info { "Container found with label $labelKey=$labelValue" }
            }

    fun stopContainer(containerId: String) {
        dockerClient.stopContainerCmd(containerId).exec().also {
            logger.info { "Container with id: $containerId stopped" }
        }
    }

    fun killContainer(containerId: String) {
        dockerClient.killContainerCmd(containerId).exec().also {
            logger.info { "Container with id: $containerId killed" }
        }
    }
}