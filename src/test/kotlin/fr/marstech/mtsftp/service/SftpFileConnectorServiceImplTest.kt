package fr.marstech.mtsftp.service

import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.matchers.shouldBe
import mu.KLogging
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.ResourceLoader
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.images.PullPolicy
import org.testcontainers.images.builder.Transferable
import org.testcontainers.utility.DockerImageName
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SftpFileConnectorServiceImplTest : StringSpec() {

    var resourceLoader: ResourceLoader = DefaultResourceLoader()

    lateinit var sftpContainer: SftpContainer
    lateinit var knownHostsFilePathString: String
    lateinit var connectionStrategy: ConnectionStrategy

    override suspend fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)

        sftpContainer = sftpContainer()
        knownHostsFilePathString = resourceLoader.getResource("classpath:ssh/known_hosts").file.toPath().toString()
        connectionStrategy = SftpPrivateKeyConnectionStrategyImpl(
            username = SFTP_USERNAME_TEST,
            privateKey = resourceLoader.getResource("classpath:ssh/id_ed25519_client").file.toPath(),
            remoteHost = sftpContainer.host,
            port = sftpContainer.sftpPort
        )
    }

    init {
        "Upload and download should work without exception with private key" {
            // Given
            val remoteFilePath: Path = Path("/share").resolve("${UUID.randomUUID()}.tmp")
            val downloadedFilePath: Path = createOutputTempDirectory().resolve(remoteFilePath.fileName)
            val connection: Connection = SftpFileConnectorServiceImpl()
                .apply { knownHostsFilePath = knownHostsFilePathString }
                .connect(connectionStrategy)

            // When
            connection.uploadFile(localFilePath = createInputTempFile(), remoteFilePath = remoteFilePath)

            // Then
            connection.listRemoteFiles(remoteFilePath.parent).contains(remoteFilePath) shouldBe true

            // And then
            connection.downloadFile(remoteFilePath = remoteFilePath, localFilePath = downloadedFilePath)
            downloadedFilePath.exists() shouldBe true
            downloadedFilePath.isRegularFile() shouldBe true

            connection.disconnect()
        }
    }

    fun sftpContainer(): SftpContainer = SftpContainer(DockerImageName.parse("atmoz/sftp"), null)
        .withCopyToContainer(
            Transferable.of(
                resourceLoader.getResource("classpath:ssh/ssh_host_ed25519_key").contentAsByteArray,
                600
            ),
            "/etc/ssh/ssh_host_ed25519_key"
        )
        .withCopyToContainer(
            Transferable.of(resourceLoader.getResource("classpath:ssh/ssh_host_rsa_key").contentAsByteArray),
            "/etc/ssh/ssh_host_rsa_key"
        )
        .withCopyToContainer(
            Transferable.of(resourceLoader.getResource("classpath:ssh/id_ed25519_client.pub").contentAsByteArray),
            "/home/${SFTP_USERNAME_TEST}/.ssh/keys/id_ed25519_client.pub"
        )
        .withEnv("SFTP_USERS", "${SFTP_USERNAME_TEST}::1001::share")
        .withCommand("/bin/sh", "-c", buildString { append("exec /usr/sbin/sshd -D -e") })
        .withExposedPorts(22)
        .withImagePullPolicy(PullPolicy.alwaysPull())
        .withStartupAttempts(1)
        .apply {
            start()
            followOutput(Slf4jLogConsumer(logger).withSeparateOutputStreams())
            sftpPort = getMappedPort(22)
        }

    companion object : KLogging() {
        // Default user for sftp testcontainers
        // https://hub.docker.com/r/atmoz/sftp
        const val SFTP_USERNAME_TEST = "foo"
    }

    class SftpContainer(
        dockerImageName: DockerImageName,
        var sftpPort: Int? = null
    ) : GenericContainer<SftpContainer>(dockerImageName)

    fun createInputTempFile(): Path = Files.createTempFile(
        Files.createTempDirectory("files-in"), "file-in", null
    )

    fun createOutputTempDirectory(): Path = Files.createTempDirectory("files-out")
}
