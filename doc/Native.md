# Going native with spring GraalVM native image support

## The Why

To improve application loading times and reduce memory footprint, we can use **GraalVM** to generate self-contained JARs
that do not require the presence of a JVM on the target environment.

These JARs are particularly valuable when targeting dockerized environments.

## Prerequisites

It will be necessary to have a JVM compatible with native compilation. At the time of writing this article, we will use
the following version (cmd `java -version`):

```shell
openjdk version "20.0.1" 2023-04-18
OpenJDK Runtime Environment Liberica-NIK-23.0.0-1 (build 20.0.1+10)
OpenJDK 64-Bit Server VM Liberica-NIK-23.0.0-1 (build 20.0.1+10, mixed mode, sharing)
```

You can easily switch between different JVM versions and update them as needed using a very handy tool, **SDKMAN!**.
After following the installation procedure (https://sdkman.io/install):

```shell
# Will install the Liberica Native Image Kit JDK 20
sdk install java 23.r20-nik

# Will set this version as default on future terminal and session opening for this user
sdk default java 23.r20-nik

# Will use this version in the current terminal
sdk use java 23.r20-nik

# To confirm the version
java -version
```

## Build Gradle

In the `build.gradle.kts` file in the `plugins` part we need to add this line:

```kotlin
id("org.graalvm.buildtools.native") version "0.9.27"
```

In the terminal, running this command in the root of the project will launch the native compilation:

```shell
gradle nativeCompile
```

It can take a while but in the end:

```shell
BUILD SUCCESSFUL in 3m 20s
9 actionable tasks: 1 executed, 8 up-to-date
```

Note the message that display where is stored our native image:

```shell
Produced artifacts:
 /myWorkspace/my-project/build/native/nativeCompile/my-app (executable)
```

If everything goes right we can launch our app with this command in the terminal:

```shell
/myWorkspace/my-project/build/native/nativeCompile/my-app
```

It will display something like that :

```shell
2023-10-20T10:52:48.995+02:00  INFO 82280 --- [           main] com.company.myapp.MtSftpApplicationKt   : Starting AOT-processed MtSftpApplicationKt using Java 20.0.1 with PID 82280 (/myWorkspace/my-project/build/native/nativeCompile/my-app started by myUser in /myWorkspace/my-project)
2023-10-20T10:52:48.995+02:00  INFO 82280 --- [           main] com.company.myapp.MtSftpApplicationKt   : No active profile set, falling back to 1 default profile: "default"
2023-10-20T10:52:49.009+02:00  INFO 82280 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat initialized with port(s): 8080 (http)
2023-10-20T10:52:49.010+02:00  INFO 82280 --- [           main] o.apache.catalina.core.StandardService   : Starting service [Tomcat]
2023-10-20T10:52:49.010+02:00  INFO 82280 --- [           main] o.apache.catalina.core.StandardEngine    : Starting Servlet engine: [Apache Tomcat/10.1.13]
2023-10-20T10:52:49.016+02:00  INFO 82280 --- [           main] o.a.c.c.C.[Tomcat].[localhost].[/]       : Initializing Spring embedded WebApplicationContext
2023-10-20T10:52:49.016+02:00  INFO 82280 --- [           main] w.s.c.ServletWebServerApplicationContext : Root WebApplicationContext: initialization completed in 20 ms
2023-10-20T10:52:49.034+02:00  INFO 82280 --- [           main] o.s.b.w.embedded.tomcat.TomcatWebServer  : Tomcat started on port(s): 8080 (http) with context path ''
2023-10-20T10:52:49.034+02:00  INFO 82280 --- [           main] com.company.myapp.MtSftpApplicationKt   : Started MtSftpApplicationKt in 0.05 seconds (process running for 0.053)
```

Note the startup time:
> Started MtSftpApplicationKt in 0.05 seconds (process running for 0.053)

A previous run on the same rig without native:
> Started MtSftpApplicationKt in 1.652 seconds (process running for 2.021)

On the memory side :

* Native 93 MB
* Classic 172 MB

It's quite an improvement.

|         | Native | Classic | Gain |
|---------|-------:|--------:|-----:|
| Startup |   50ms |  1652ms |  97% |
| Memory  |   93MB |   172MB |  46% |

These metrics are taken immediately after the service starts.

## Going native with spring GraalVM native image support conclusion

In this part of our serie we have configured our gradle file to use native compilation. 

We used a tool to install and switch to a GraalVM comptatible JDK, AKA SDKMAN.

We have created and run our first artifact using GraalVM.

We were able to see improvement on startup time and memory consumption.

## Conclusions

In conclusion, we have covered the various steps to use JSch for sending and receiving files with an SSH/SFTP server.

We have set up a test using TestContainers to dynamically spin up a Docker image that served as our server for
conducting our send/receive tests.

We used GraalVM and native compilation to improve the performance of our app.

## Resources

To further expand your knowledge:

* https://www.whoz.com/fr/
* https://github.com/mwiede/jsch
* https://mkyong.com/java/file-transfer-using-sftp-in-java-jsch/
* https://www.ssh.com/academy/ssh/sftp-ssh-file-transfer-protocol
* https://linuxize.com/post/how-to-use-linux-sftp-command-to-transfer-files/
* https://testcontainers.com/guides/getting-started-with-testcontainers-for-java/
* https://engineering.zalando.com/posts/2021/02/integration-tests-with-testcontainers.html
* https://hub.docker.com/r/atmoz/sftp
* https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html
* https://www.baeldung.com/spring-native-intro
* https://sdkman.io/
