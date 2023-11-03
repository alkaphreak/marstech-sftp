# marstech-sftp

Test implem of **JSch** with **Kotlin**, **Kotest**, **Testcontainers** and **Spring boot Native**

1. [JSCH Howto](doc/Jsch.md)
2. [JSCH testing and TestContainers Howto](doc/Testing.md)
3. [Going Native Howto](doc/Native.md)

## Conclusions

In conclusion, we have covered the various steps to use JSch for sending and receiving files with an SSH/SFTP server.

We have set up a test using TestContainers to dynamically spin up a Docker image that served as our server for
conducting our send/receive tests.

We used GraalVM and native compilation to improve the performance of our app.

## Resources

To further expand your knowledge

### JSCH part

* https://github.com/mwiede/jsch
* https://mkyong.com/java/file-transfer-using-sftp-in-java-jsch/
* https://www.ssh.com/academy/ssh/sftp-ssh-file-transfer-protocol
* https://linuxize.com/post/how-to-use-linux-sftp-command-to-transfer-files/

### Testing part

* https://www.baeldung.com/kotlin/kotest
* https://testcontainers.com/guides/getting-started-with-testcontainers-for-java/
* https://engineering.zalando.com/posts/2021/02/integration-tests-with-testcontainers.html
* https://hub.docker.com/r/atmoz/sftp

### Native build part

* https://docs.spring.io/spring-boot/docs/current/reference/html/native-image.html
* https://www.baeldung.com/spring-native-intro
* https://sdkman.io/
