# SFTP implem

Test implem of **JSch** with **Kotlin**, **Testcontainers** and **Spring boot Native**

## Context

At [Whoz](https://www.whoz.com/fr/), we needed the capability to securely retrieve files deposited by our clients in a
directory accessible via SFTP during data imports.

We aimed to integrate our in-house data import tool, developed in Java/Kotlin with Spring Boot. To achieve this, we
required an implementation of the SSH/SFTP protocol within our application.

## Prerequisites

To follow this article, it is necessary to have some knowledge of Kotlin and a basic understanding of how SSH and SFTP
work.

We will briefly introduce the usage of Kotest, TestContainers, and JSch, but this article is not intended to be
comprehensive in those areas.

We assume that you have an up-to-date JVM (>=17) installed, along with Gradle, and that the project has been freshly
created using https://start.spring.io/.

## JSCH

We initially considered the Java library JSCH, which is quite popular and has a relatively extensive online resource
base. However, the official version provided by Jcraft (http://www.jcraft.com/jsch/) is no longer actively maintained,
and our SSH server uses encryption algorithms that are not supported by this version.

The final decision was to go with a fork that aligns with our requirements: https://github.com/mwiede/jsch.

### Build Gradle

In the `build.gradle.kts` file, you should add the following dependencies to your Gradle build script. These
dependencies are necessary for your project:

```groovy
// https://mvnrepository.com/artifact/com.github.mwiede/jsch
implementation("com.github.mwiede:jsch:0.2.11")
```

### API

We are going to create several interfaces that correspond to common needs.

#### Connection

An API for the core features.

```kotlin
import java.nio.file.Path

interface Connection {

    fun disconnect()
    fun downloadFile(remoteFilePath: Path, localFilePath: Path)
    fun listRemoteFiles(remoteDirectoryPath: Path): Set<Path>
    fun uploadFile(localFilePath: Path, remoteFilePath: Path)
}

```

#### ConnectionStrategy

This interface will allow us to select connections by password or key exchange.

The `build` method allows us to retrieve an instance of `JSch` with the `KnownHosts` defined.

```kotlin
import com.jcraft.jsch.JSch

interface ConnectionStrategy {
    fun build(knownHostsFilePath: String): JSch = JSch().also { it.setKnownHosts(knownHostsFilePath) }
}
```

#### FileConnectorService

Our service.

```kotlin
interface FileConnectorService {
    fun connect(strategy: ConnectionStrategy): Connection
}
```

### Implementation

#### SftpConnectionImpl

Here is our initial implementation regarding the connection part.

```kotlin
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import mu.KLogging
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.pathString

class SftpConnectionImpl(private val session: Session) : Connection {

    private fun channelSftp() = (session.openChannel("sftp").apply { connect() } as ChannelSftp)

    override fun uploadFile(localFilePath: Path, remoteFilePath: Path) {
        channelSftp().apply {
            logger.debug("upload file from {} to {}", localFilePath, remoteFilePath)
            put(localFilePath.pathString, remoteFilePath.pathString)
            exit()
            logger.debug("file uploaded")
        }
    }

    override fun downloadFile(remoteFilePath: Path, localFilePath: Path) {
        channelSftp().apply {
            logger.debug("download file from {} to {}", remoteFilePath, localFilePath)
            get(remoteFilePath.pathString, localFilePath.pathString)
            exit()
            logger.debug("file downloaded")
        }
    }

    override fun listRemoteFiles(remoteDirectoryPath: Path): Set<Path> = (
            channelSftp().apply {
                logger.debug("listing files from {}", remoteDirectoryPath)
                cd(remoteDirectoryPath.pathString)
            }.ls(remoteDirectoryPath.pathString).stream() as Stream<ChannelSftp.LsEntry>
            ).map { entry -> remoteDirectoryPath.resolve(entry.filename) }
        .toList()
        .toSet()
        .apply<Set<Path>> { logger.debug { "end file list. Found $size files" } }

    override fun disconnect() = session.disconnect()

    companion object : KLogging()
}
```

First, the **upload**, which is relatively simple with the opening of the SFTP session, file copying (put), and finally,
closing the session.

Similarly, the **download** follows the same process: session opening, downloading, and closing.

Regarding the use of `put` and `get`, the JSCH API allows the use of other types of objects based on your needs, such as
**InputStream**, **OutputStream**, and more.

I encourage you to read the documentation or explore the possibilities in this regard.

The **listRemoteFiles** method allows you to retrieve the list of remote files.

Finally, use **disconnect** to exit the session gracefully.

#### SftpPasswordConnectionStrategyImpl

Once instantiated with the appropriate parameters, this class allows you to establish a password-based connection.

```kotlin
import com.jcraft.jsch.Session
import mu.KLogging

class SftpPasswordConnectionStrategyImpl(
    private val username: String,
    private val password: String,
    private val remoteHost: String,
    private val port: Int?,
    private val strictHostChecking: Boolean = true
) : ConnectionStrategy {
    fun connect(knownHostsFilePath: String): Session = build(knownHostsFilePath).run {
        logger.debug("connectWithPassword on host $remoteHost:$port")
        port?.let { getSession(username, remoteHost, it) } ?: getSession(username, remoteHost)
    }.also {
        it.setPassword(password)
        it.setConfig("StrictHostKeyChecking", if (strictHostChecking) "yes" else "no")
        it.setConfig("HashKnownHosts", "yes")
        it.connect()
        logger.debug("session opened on remote host")
    }

    companion object : KLogging()
}
```

We start by calling `build` to obtain an instance of `JSch` from which we retrieve an instance of `Session`. We then
configure this session with the password and, if necessary, some parameters before finally establishing the connection.

#### SftpPrivateKeyConnectionStrategyImpl

Once instantiated with the appropriate parameters, this class allows you to establish a private key connection.

```kotlin
import com.jcraft.jsch.Session
import mu.KLogging
import java.nio.file.Path
import kotlin.io.path.pathString

class SftpPrivateKeyConnectionStrategyImpl(
    private val username: String,
    private val privateKey: Path,
    private val remoteHost: String,
    private val port: Int?,
    private val strictHostChecking: Boolean = true
) : ConnectionStrategy {
    fun connect(knownHostsFilePath: String): Session = build(knownHostsFilePath)
        .apply { addIdentity(privateKey.pathString) }
        .run {
            logger.debug("connectWithPrivateKey on host $remoteHost:$port")
            port?.let { getSession(username, remoteHost, it) } ?: getSession(username, remoteHost)
        }.also {
            it.setConfig("StrictHostKeyChecking", if (strictHostChecking) "yes" else "no")
            it.setConfig("HashKnownHosts", "yes")
            it.connect()
            logger.debug("session opened on remote host")
        }

    companion object : KLogging()
}
```

We start by calling `build` to obtain an instance of `JSch`. We configure it with the client's private key
using `addIdentity`. Then, we retrieve an instance of `Session`, which we can configure with additional parameters if
needed before finally establishing the connection.

### SftpFileConnectorServiceImpl

Our implementation of the `FileConnectorService`.

```kotlin
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

@Service
class SftpFileConnectorServiceImpl : FileConnectorService {

    @Value("\${mtsftp.knownHosts:''}")
    lateinit var knownHostsFilePath: String

    override fun connect(strategy: ConnectionStrategy): Connection = when (strategy) {
        is SftpPasswordConnectionStrategyImpl -> SftpConnectionImpl(strategy.connect(knownHostsFilePath))
        is SftpPrivateKeyConnectionStrategyImpl -> SftpConnectionImpl(strategy.connect(knownHostsFilePath))
        else -> throw UnsupportedOperationException("Unsupported connection strategy: $strategy")
    }
}
```

First, the `knownHostsFilePath` property can be defined via injection from an `application.properties`
or `application.yaml` file, allowing us to configure local keys corresponding to remote hosts.

Then, depending on the `ConnectionStrategy` type passed as a parameter, the `connect` method will initiate a connection
via either a password or a private key and return an instance of `Connection`.

### Testing

For testing with Kotlin, we will use the Kotest testing framework and TestContainers, an open-source library for
simplifying integration tests using dynamically created containers.

You can add the following dependencies to your `build.gradle.kts` file:

```kotlin
testImplementation("org.springframework.boot:spring-boot-starter-test")
testImplementation("org.springframework.boot:spring-boot-testcontainers")
testImplementation("io.kotest:kotest-runner-junit5-jvm:5.7.2")
```

#### SftpFileConnectorServiceImplTest

In our test class, we have a single test method that performs the following steps in order:

1. Establish a connection.
2. Upload a file.
3. Retrieve a list of files.
4. Download a file.
5. Finally, disconnect.

```kotlin
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
import java.awt.datatransfer.Transferable
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
            port = sftpContainer.sftpPort,
            strictHostChecking = false
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
        .withExposedPorts(22).withImagePullPolicy(PullPolicy.alwaysPull()).withStartupAttempts(1)
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
    ) :
        GenericContainer<SftpContainer>(dockerImageName)

    fun createInputTempFile(): Path = Files.createTempFile(
        Files.createTempDirectory("files-in"), "file-in", null
    )

    fun createOutputTempDirectory(): Path = Files.createTempDirectory("files-out")
}
```

##### Test Preparation

In the `beforeTest` method, we will obtain an instance of `SftpContainer` corresponding to our preconfigured Docker
container using the `sftpContainer` method that we will describe below.

We retrieve our `knownHosts` file from `knownHostsFilePathString` using a call to our `ResourceLoader`.

We set our connection strategy, which, in this case, is via a private key using `SftpPrivateKeyConnectionStrategyImpl`,
and we provide the necessary parameters for establishing the connection, such as:

- The username on the remote host, which is set to `"foo"` by default in our container's image.
- The private key corresponding to the public key on the remote host, obtained through a call to the `ResourceLoader`.
- The IP address of the remote host, retrieved directly from our container instance.
- The connection port, obtained directly from our container instance (default is 22).

##### The test

Our test, "Upload and download should work without exception with a private key," will start by defining:

- `remoteFilePath`: The location of the file we want to create/upload on the remote server.
- `downloadedFilePath`: The location of the file we want to create/download on the client.
- `connection`: An instance of `Connection` obtained by calling our `SftpFileConnectorServiceImpl` service with our list
  of known hosts and connection strategy.

Sequentially, we perform the following steps:

1. Upload the file.
2. Test for the presence of the file on the server.
3. Download the file and test its presence locally.
4. Disconnect.

##### Testcontainers

The `SftpContainer` class and the `sftpContainer` method will allow us to define, configure, and launch our test
container, which serves as the remote host or server.

In the `sftpContainer` method, we will perform several steps:

- Define the base Docker image, which is `"atmoz/sftp"` in this case.
- Copy files to the image at the correct locations, sometimes specifying Unix permissions. These files include:
    - The container's private key: `ssh_host_ed25519_key`
    - Known host keys: `ssh_host_rsa_key`
    - The client's private key: `id_ed25519_client.pub`
- Add an environment variable corresponding to our user on the container.
- Define the Unix command that starts the SSHd daemon on the container.
- Expose the SSH port outside the container.
- Specify the image retrieval policy.
- Define the number of startup attempts for the container in case of an error.
- Start the container.
- Map the container's message output to our `Logger` instance.
- Retrieve the SFTP/SSH port directly from the container for our `SftpContainer` instance.

## Conclusions

In conclusion, we have covered the various steps to use JSch for sending and receiving files with an SSH/SFTP server.

We have set up a test using TestContainers to dynamically spin up a Docker image that served as our server for
conducting our send/receive tests.

## Resources

To further expand your knowledge in this area:

* https://www.whoz.com/fr/
* https://github.com/mwiede/jsch
* https://mkyong.com/java/file-transfer-using-sftp-in-java-jsch/
* https://www.ssh.com/academy/ssh/sftp-ssh-file-transfer-protocol
* https://linuxize.com/post/how-to-use-linux-sftp-command-to-transfer-files/
* https://testcontainers.com/guides/getting-started-with-testcontainers-for-java/
* https://hub.docker.com/r/atmoz/sftp
