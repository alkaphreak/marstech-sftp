# Testing with Kotest and TestContainers

Second part of our test implem of **JSch** with **Kotlin**, **Kotest**, **Testcontainers** and **Spring boot Native**
serie.

## Dependencies

For testing with Kotlin, we will use the `Kotest` testing framework and `TestContainers`, an open-source library for
simplifying integration tests using dynamically created containers.

You can add the following dependencies to your `build.gradle.kts` file:

```kotlin
testImplementation("org.springframework.boot:spring-boot-starter-test")
testImplementation("org.springframework.boot:spring-boot-testcontainers")
testImplementation("io.kotest:kotest-runner-junit5-jvm:5.7.2")
```

## SftpFileConnectorServiceImplTest

In our test class, we have a single test method that performs the following steps in order as you can see in the init
part :

1. Establish a connection.
2. Upload a file.
3. Retrieve a list of files.
4. Download a file.
5. Finally, disconnect.

```kotlin
import java.awt.datatransfer.Transferable
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

{

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
            val remoteFilePath: Path = Path("/share")
                .resolve("${UUID.randomUUID()}.tmp")
            val downloadedFilePath: Path = createOutputTempDirectory()
                .resolve(remoteFilePath.fileName)
            val connection: Connection = SftpFileConnectorServiceImpl()
                .apply { knownHostsFilePath = knownHostsFilePathString }
                .connect(connectionStrategy)

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
    }

    fun sftpContainer(): SftpContainer = SftpContainer(DockerImageName.parse("atmoz/sftp"))
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
            "/home/${SFTP_USERNAME_TEST}/.ssh/keys/id_ed25519_client.pub"
        )
        .withEnv("SFTP_USERS", "${SFTP_USERNAME_TEST}::1001::share")
        .withTmpFs(makeTmpFs(instanceUUID))
        .withCommand(
            "/bin/sh",
            "-c",
            buildString { append("exec /usr/sbin/sshd -D -e") }
        )
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

    private fun createInputTempFile(): Path =
        Files.createTempFile(
            Files.createTempDirectory("files-in"),
            "file-in",
            null
        )

    private fun createOutputTempDirectory(): Path =
        Files.createTempDirectory("files-out")

    private fun makeTmpFs(folderName: String): MutableMap<String, String> =
        Collections.singletonMap(
            Files.createTempDirectory(folderName).toString(),
            "rw"
        )

    class SftpContainer(
        dockerImageName: DockerImageName,
        var sftpPort: Int? = null
    ) : GenericContainer<SftpContainer>(dockerImageName)
}
```

### Test Preparation

In the `beforeTest` method, we will obtain an instance of `SftpContainer` corresponding to our preconfigured Docker
container using the `sftpContainer` method that we will describe in another part.

We retrieve our `knownHosts` file from `knownHostsFilePathString` using a call to our `ResourceLoader`.

We set our connection strategy, which, in this case, is via a private key using `SftpPrivateKeyConnectionStrategyImpl`,
and we provide the necessary parameters for establishing the connection, such as:

- The username on the remote host, which is set to `"foo"` by default in our container's image.
- The private key corresponding to the public key on the remote host, obtained through a call to the `ResourceLoader`.
- The IP address of the remote host, retrieved directly from our container instance (should be localhost).
- The connection port, obtained directly from our container instance (default is 22).

### The test

Our test, `Upload and download should work without exception with private key` will start by defining in the `Given`
part:

- `remoteFilePath`: The location of the file we want to create/upload on the remote server.
- `downloadedFilePath`: The location of the file we want to create/download on the client side.
- `connection`: An instance of `Connection` obtained by calling our `SftpFileConnectorServiceImpl` service with
  our `knownHostsFilePath` and `ConnectionStrategy`.

Sequentially, we perform the following steps:

1. Upload the file.
2. Test for the presence of the file on the server.
3. Download the file and test its presence locally.
4. Disconnect.

## Testcontainers

The `SftpContainer` class and the `sftpContainer` method will allow us to define, configure, and launch our test
container, which serves as the remote host or server.

In the `sftpContainer` method, we will perform several steps:

- Define the base Docker image, which is `"atmoz/sftp"` in this case.
- Copy files to the image at the correct locations, sometimes specifying Unix permissions. These files include:
    - The container's private key: `ssh_host_ed25519_key`
    - Known host keys: `ssh_host_rsa_key`
    - The client's private key: `id_ed25519_client.pub`
- Add an environment variable corresponding to our user on the container.
- Define a `tmpFs` path for storing data in host memory, useful to speed up our tests.
- Define the Unix command that starts the SSHd daemon on the container.
- Expose the SSH port outside the container.
- Specify the image retrieval policy.
- Define the number of startup attempts for the container in case of an error.
- Define the container reuse policy to true (still experimental).
- Put a label on it.
- Start the container.
- Map the container's message output to our `Logger` instance.
- Retrieve the SFTP/SSH port directly from the container for our `SftpContainer` instance.

Through this configuration we are able to mont on the fly a remote server to run our tests with.

It can be reused to speed up further test runs.

## JSCH sftp service testing conclusion

We have seen how to perform our first test using Kotest and our SFTP service. In the meantime, we used TestContainers to
avoid the need for a pre-existing SSH server.

From here, we can improve our service to build features and provide business values.

In the next part, we will build a native image of our service to improve startup time and memory consumption.

[Going native with spring GraalVM native image support](./Native.md)