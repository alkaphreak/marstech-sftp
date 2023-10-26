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
import org.testcontainers.utility.LogUtils
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
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
    lateinit var instanceUUID: String

    override suspend fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)
        instanceUUID = "68928936-327b-4335-ac43-f6b27b00c881"

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

    fun sftpContainer(): SftpContainer = SftpContainer(DockerImageName.parse("atmoz/sftp"))
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
        .withTmpFs(makeTmpFs(instanceUUID))
        .withCommand("/bin/sh", "-c", buildString { append("exec /usr/sbin/sshd -D -e") })
        .withExposedPorts(22)
        .withImagePullPolicy(PullPolicy.ageBased(Duration.ofDays(30)))
        .withStartupAttempts(1)
        .withReuse(true)
        .withLabel("reuse.UUID", instanceUUID)
        .apply {
            start()
            LogUtils.followOutput(
                this.dockerClient,
                this.containerId,
                Slf4jLogConsumer(logger).withSeparateOutputStreams()
            )
            sftpPort = getMappedPort(22)
        }

    companion object : KLogging() {
        // Default user for sftp testcontainers
        // https://hub.docker.com/r/atmoz/sftp
        const val SFTP_USERNAME_TEST = "foo"
    }

    private fun createInputTempFile(): Path = Files.createTempFile(
        Files.createTempDirectory("files-in"),
        "file-in",
        null
    )

    private fun createOutputTempDirectory(): Path = Files.createTempDirectory("files-out")

    private fun makeTmpFs(folderName: String): MutableMap<String, String> = Collections.singletonMap(
        Files.createTempDirectory(folderName).toString(),
        "rw"
    )

    class SftpContainer(
        dockerImageName: DockerImageName,
        var sftpPort: Int? = null
    ) : GenericContainer<SftpContainer>(dockerImageName)
}
