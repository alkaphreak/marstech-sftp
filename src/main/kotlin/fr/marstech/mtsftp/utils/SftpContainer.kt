package fr.marstech.mtsftp.utils

import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.ResourceLoader
import org.testcontainers.containers.GenericContainer
import org.testcontainers.images.builder.Transferable

class SftpContainer(
    dockerImageName: String = IMAGE
) : GenericContainer<SftpContainer>(dockerImageName) {

    lateinit var instanceUUID: String

    private var resourceLoader: ResourceLoader = DefaultResourceLoader()

    init {
        this
            .withCopyToContainer(
                Transferable.of(
                    resourceLoader.getResource(
                        "classpath:ssh/ssh_host_ed25519_key"
                    ).contentAsByteArray,
                    600
                ),
                "/etc/ssh/ssh_host_ed25519_key"
            )
            .withCopyToContainer(
                Transferable.of(
                    resourceLoader.getResource(
                        "classpath:ssh/ssh_host_rsa_key"
                    ).contentAsByteArray
                ),
                "/etc/ssh/ssh_host_rsa_key"
            )
            .withCopyToContainer(
                Transferable.of(
                    resourceLoader.getResource(
                        "classpath:ssh/id_ed25519_client.pub"
                    ).contentAsByteArray
                ),
                "/home/$SFTP_USERNAME_TEST/.ssh/keys/id_ed25519_client.pub"
            )
            .withEnv("SFTP_USERS", "$SFTP_USERNAME_TEST::1001::share")
            .withCommand(
                "/bin/sh",
                "-c",
                buildString { append("exec /usr/sbin/sshd -D -e") }
            )
    }

    companion object {
        const val TYPE: String = "sftp"
        const val IMAGE: String = "atmoz/sftp"
        const val PORT: Int = 22

        // Default user for sftp testcontainers
        // https://hub.docker.com/r/atmoz/sftp
        const val SFTP_USERNAME_TEST = "foo"
    }
}