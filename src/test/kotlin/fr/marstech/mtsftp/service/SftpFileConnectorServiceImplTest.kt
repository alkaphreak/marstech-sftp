package fr.marstech.mtsftp.service

import com.fasterxml.jackson.databind.ObjectMapper
import fr.marstech.mtsftp.utils.*
import fr.marstech.mtsftp.utils.SftpContainer.Companion.SFTP_USERNAME_TEST
import io.github.oshai.kotlinlogging.KLogger
import io.github.oshai.kotlinlogging.KotlinLogging
import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.core.test.TestCase
import io.kotest.core.test.TestResult
import io.kotest.matchers.shouldBe
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.ResourceLoader
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

private val logger: KLogger = KotlinLogging.logger {}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SftpFileConnectorServiceImplTest(
    private val objectMapper: ObjectMapper,
    private val resourceLoader: ResourceLoader
) : StringSpec() {

    private lateinit var redisContainer: RedisContainer
    private lateinit var sftpContainer: SftpContainer
    private lateinit var knownHostsFilePathString: String
    private lateinit var connectionStrategy: ConnectionStrategy

    /**
     * Run once before the first test method for all tests in class
     */
    override suspend fun beforeSpec(spec: Spec) {
        redisContainer = RedisContainer().apply {
            startOrReuseUniqueInstance(
                RedisContainer.INSTANCE_UUID,
                exposedPorts = intArrayOf(RedisContainer.PORT),
                logger = logger.toSlf4j()
            )
        }
    }

    /**
     * Run before each test method
     */
    override suspend fun beforeTest(testCase: TestCase) {
        val instanceUUID = redisContainer.getFreeInstanceUUID(SftpContainer.TYPE, objectMapper)
        sftpContainer = SftpContainer().apply {
            startOrReuseUniqueInstance(
                instanceUUID,
                exposedPorts = intArrayOf(SftpContainer.PORT),
                logger = logger.toSlf4j()
            )
            this.instanceUUID = instanceUUID
        }
        knownHostsFilePathString = resourceLoader.getResource("classpath:ssh/known_hosts").file.toPath().toString()
        connectionStrategy = SftpPrivateKeyConnectionStrategyImpl(
            username = SFTP_USERNAME_TEST,
            privateKey = resourceLoader.getResource("classpath:ssh/id_ed25519_client").file.toPath(),
            remoteHost = sftpContainer.host,
            port = sftpContainer.getMappedPort(SftpContainer.PORT),
            strictHostChecking = true
        )
    }

    override suspend fun afterTest(testCase: TestCase, result: TestResult) {
        redisContainer.freeInstance(SftpContainer.TYPE, objectMapper, sftpContainer.instanceUUID)
    }

    override suspend fun afterSpec(spec: Spec) {
        redisContainer.freeInstances(SftpContainer.TYPE, objectMapper)
    }

    init {
        "Upload and download should work without exception with private key" {
            // Given
            val remoteFilePath = Path("/share/${UUID.randomUUID()}.tmp")
            val downloadedFilePath: Path = createOutputTempDirectory().resolve(remoteFilePath.fileName)
            val connection: Connection =
                SftpFileConnectorServiceImpl(knownHostsFilePathString).connect(connectionStrategy)

            // When
            connection.uploadFile(
                localFilePath = createInputTempFile(),
                remoteFilePath = remoteFilePath
            )

            // Then
            connection
                .listRemoteFiles(remoteFilePath.parent)
                .contains(remoteFilePath) shouldBe true

            // And then
            connection.downloadFile(
                remoteFilePath = remoteFilePath,
                localFilePath = downloadedFilePath
            )
            downloadedFilePath.exists() shouldBe true
            downloadedFilePath.isRegularFile() shouldBe true

            // And Finally
            connection.disconnect()
        }

        "Test Redis Container" {
            val uuids: List<String> = (1..3).map {
                redisContainer.getFreeInstanceUUID(SftpContainer.TYPE, objectMapper)
            }
            uuids.size shouldBe 3

            redisContainer.list().filter { it.labels[getReuseLabel()] in uuids }.size shouldBe 3

            uuids.forEach { uuid ->
                redisContainer.freeInstance(SftpContainer.TYPE, objectMapper, uuid)

                // TODO find why it doesn't work
                redisContainer.killInstance(uuid)
            }
        }
    }
}
