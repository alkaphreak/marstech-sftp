package fr.marstech.mtsftp.utils

import com.github.dockerjava.api.DockerClient
import com.github.dockerjava.api.model.Container
import com.github.dockerjava.core.DockerClientBuilder
import com.github.dockerjava.netty.NettyDockerCmdExecFactory


object DockerUtils {

    private fun dockerClient(): DockerClient = DockerClientBuilder
        .getInstance()
        .withDockerCmdExecFactory(NettyDockerCmdExecFactory())
        .build()

    fun getContainerFromLabel(labelKey: String, labelValue: String): Container? = dockerClient()
        .listContainersCmd()
        .withShowAll(true)
        .exec()
        .find { it.labels[labelKey].equals(labelValue) }

    fun stopContainer(containerId: String) {
        dockerClient().stopContainerCmd(containerId).exec()
    }

    fun killContainer(containerId: String) {
        dockerClient().killContainerCmd(containerId).exec()
    }
}