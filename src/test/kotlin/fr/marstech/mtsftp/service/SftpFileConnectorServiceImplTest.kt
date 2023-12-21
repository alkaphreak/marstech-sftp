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
import io.kotest.extensions.spring.SpringExtension
import io.kotest.extensions.spring.SpringTestExtension
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.ResourceLoader
import java.nio.file.Path
import java.util.*
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.isRegularFile

private val logger: KLogger = KotlinLogging.logger {}

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SftpFileConnectorServiceImplTest : StringSpec() {

    @Autowired
    lateinit var objectMapper: ObjectMapper

    override fun extensions(): List<SpringTestExtension> = listOf(SpringExtension)

    var resourceLoader: ResourceLoader = DefaultResourceLoader()

    lateinit var redisContainer: RedisContainer
    lateinit var sftpContainer: SftpContainer

    lateinit var knownHostsFilePathString: String
    lateinit var connectionStrategy: ConnectionStrategy

    /**
     * Run once before the first test method for all tests in class
     */
    override suspend fun beforeSpec(spec: Spec) {
        super.beforeSpec(spec)

        redisContainer = RedisContainer().startOrReuseUniqueInstance(
            instanceUUID = RedisContainer.INSTANCE_UUID,
            exposedPorts = intArrayOf(RedisContainer.PORT),
            logger = logger.toSlf4j2()
        ) as RedisContainer
    }

    /**
     * Run before each test method
     */
    override suspend fun beforeTest(testCase: TestCase) {
        super.beforeTest(testCase)

        val instanceUUID: String = redisContainer.getFreeInstanceUUID(SftpContainer.TYPE, objectMapper)
        sftpContainer = (SftpContainer().startOrReuseUniqueInstance(
            instanceUUID = instanceUUID,
            exposedPorts = intArrayOf(SftpContainer.PORT),
            logger = logger.toSlf4j2()
        ) as SftpContainer).also {
            it.instanceUUID = instanceUUID
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
        super.afterTest(testCase, result)

        redisContainer.freeInstance(
            type = SftpContainer.TYPE,
            mapper = objectMapper,
            instanceUUID = sftpContainer.instanceUUID
        )
    }

    override suspend fun afterSpec(spec: Spec) {
        super.afterSpec(spec)

        redisContainer.freeInstances(
            type = SftpContainer.TYPE,
            mapper = objectMapper
        )
    }

    init {
        "Upload and download should work without exception with private key" {
            // Given
            val remoteFilePath: Path = Path("/share")
                .resolve("${UUID.randomUUID()}.tmp")
            val downloadedFilePath: Path = createOutputTempDirectory()
                .resolve(remoteFilePath.fileName)
            val connection: Connection = SftpFileConnectorServiceImpl(
                knownHostsFilePath = knownHostsFilePathString
            ).connect(connectionStrategy)

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
            redisContainer shouldNotBe null
            val uuids: List<String> = listOf(
                redisContainer.getFreeInstanceUUID(
                    type = SftpContainer.TYPE,
                    mapper = objectMapper
                ),
                redisContainer.getFreeInstanceUUID(
                    type = SftpContainer.TYPE,
                    mapper = objectMapper
                ),
                redisContainer.getFreeInstanceUUID(
                    type = SftpContainer.TYPE,
                    mapper = objectMapper
                )
            )
            uuids.size shouldBe 3
            uuids.filter { false }.size shouldBe 0

            uuids.forEach {
                redisContainer.freeInstance(
                    type = SftpContainer.TYPE,
                    mapper = objectMapper,
                    instanceUUID = it
                )
            }

            redisContainer.killInstance(
                redisContainer.getFreeInstanceUUID(type = SftpContainer.TYPE, mapper = objectMapper)
                    .also {}
            )
        }
    }
}
