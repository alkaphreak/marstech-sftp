package fr.marstech.mtsftp.utils

import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.ResourceLoader
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.Transferable

class SftpContainer(
    dockerImageName: String = IMAGE
) : GenericContainer<SftpContainer>(dockerImageName) {

    lateinit var instanceUUID: String

    private val resourceLoader: ResourceLoader = DefaultResourceLoader()

    init {
        val sshFiles = mapOf(
            "classpath:ssh/ssh_host_ed25519_key" to "/etc/ssh/ssh_host_ed25519_key",
            "classpath:ssh/ssh_host_rsa_key" to "/etc/ssh/ssh_host_rsa_key",
            "classpath:ssh/id_ed25519_client.pub" to "/home/$SFTP_USERNAME_TEST/.ssh/keys/id_ed25519_client.pub"
        )

        sshFiles.forEach { (resourcePath, containerPath) ->
            withCopyToContainer(
                Transferable.of(resourceLoader.getResource(resourcePath).contentAsByteArray),
                containerPath
            )
        }

        withEnv("SFTP_USERS", "$SFTP_USERNAME_TEST::1001::share")
        withCommand("/bin/sh", "-c", "exec /usr/sbin/sshd -D -e")
    }

    companion object {
        const val TYPE: String = "sftp"
        const val IMAGE: String = "atmoz/sftp"
        const val PORT: Int = 22
        const val SFTP_USERNAME_TEST = "foo"
    }
}