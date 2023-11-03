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

You may have to know how to generate ssh privates and public keys, as well as a knownhost file.

We will briefly introduce the usage of Kotest, TestContainers, and JSch, but this article is not intended to be
comprehensive in those areas.

We assume that you have an up-to-date JVM (>=17) installed, along with Gradle, and that the project has been freshly
created using https://start.spring.io/.

## JSCH

We initially considered the Java library JSCH, which is quite popular and has a relatively extensive online resource
base. However, the official version provided by Jcraft (http://www.jcraft.com/jsch/) is no longer actively maintained,
and our SSH server uses encryption algorithms that are not supported by this version.

The final decision was to go with a fork that aligns with our requirements: https://github.com/mwiede/jsch.

## Build Gradle

In the `build.gradle.kts` file, you should add the following dependencies to your Gradle build script. These
dependencies are necessary for your project:

```groovy
// https://mvnrepository.com/artifact/com.github.mwiede/jsch
implementation("com.github.mwiede:jsch:0.2.11")
```

## API

We are going to create several interfaces that correspond to common needs.

### Connection

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

### ConnectionStrategy

This interface will allow us to select connections by password or key exchange.

The `build` method allows us to retrieve an instance of `JSch` with the `KnownHosts` defined.

```kotlin
import com.jcraft.jsch.JSch

interface ConnectionStrategy {
    fun build(knownHostsFilePath: String): JSch = JSch().also { it.setKnownHosts(knownHostsFilePath) }
}
```

### FileConnectorService

Our service reusing the ConnectionStrategy.

```kotlin
interface FileConnectorService {
    fun connect(strategy: ConnectionStrategy): Connection
}
```

## Implementation

### SftpConnectionImpl

Here is our initial implementation regarding the connection part.

```kotlin
import com.jcraft.jsch.ChannelSftp
import com.jcraft.jsch.Session
import mu.KLogging
import java.nio.file.Path
import java.util.stream.Stream
import kotlin.io.path.pathString

class SftpConnectionImpl(private val session: Session) : Connection {

    private fun channelSftp(): ChannelSftp = (
            session.openChannel("sftp")
                .apply { connect() } as ChannelSftp
            )

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

First the **channelSftp** method will open and connect our sftp session.

Then, the **upload**, which is relatively simple with :

* the opening of the SFTP session
* file copying (put)
* and finally, closing the session.

Similarly, the **download** follows the same process:

* session opening
* download
* and closing

Regarding the use of `put` and `get`, the JSCH API allows the use of other types of objects based on your needs, such as
**InputStream**, **OutputStream**, and more.

I encourage you to read the documentation or explore the possibilities in this regard.

The **listRemoteFiles** method allows you to retrieve the list of remote files.

Finally, use **disconnect** to exit the session gracefully.

### SftpPasswordConnectionStrategyImpl

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

### SftpPrivateKeyConnectionStrategyImpl

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
        is SftpPasswordConnectionStrategyImpl -> SftpConnectionImpl(
            strategy.connect(knownHostsFilePath)
        )
        is SftpPrivateKeyConnectionStrategyImpl -> SftpConnectionImpl(
            strategy.connect(knownHostsFilePath)
        )
        else -> throw UnsupportedOperationException(
            "Unsupported connection strategy: $strategy"
        )
    }
}
```

First, the `knownHostsFilePath` property can be defined via injection from an `application.properties`
or `application.yaml` file, allowing us to configure local keys corresponding to remote hosts.

Then, depending on the `ConnectionStrategy` type passed as a parameter, the `connect` method will initiate a connection
via either a password or a private key and return an instance of `Connection`.

## JSCH sftp service implementation conclusion

We've seen how to build our first service using JSCH with Kotlin and in the meantime two Authentication ways for SSH.

We have a way to do basic SFTP action:

- connect
- disconnect
- list
- download
- upload

From here, we can start using our service to perform our first file transfers with an existing SSH server.

In the next section, we will test our service using an on-the-fly provisioned SSH server.

[JSCH testing and TestContainers Howto](./Testing.md)
