# marstech-sftp

## Efficient SFTP Testing with JSch, Kotlin, Testcontainers, and Spring Boot Native

Explore a streamlined approach to testing SFTP operations using JSch, Kotlin, Testcontainers,
and the power of Spring Boot Native for optimized performance.

## Table of contents

1. [JSCH Howto](doc/Jsch.md)
2. [JSCH testing and TestContainers Howto](doc/Testing.md)
3. [Going Native Howto](doc/Native.md)
4. [Tests containers, going further](doc/TODO)

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

### Tests Containers

* https://kotest.io/docs/framework/lifecycle-hooks.html

---

## Hi there 👋

Here is my Linktree: https://linktr.ee/StephaneRobinJob

Here is my refreal link on digital ocean: https://m.do.co/c/be389fc15e1a

Get $200 in free DigitalOcean credits to deploy your next project! Perfect for developers starting their cloud journey.

[![DigitalOcean Referral Badge](https://web-platforms.sfo2.cdn.digitaloceanspaces.com/WWW/Badge%201.svg)](https://www.digitalocean.com/?refcode=be389fc15e1a&utm_campaign=Referral_Invite&utm_medium=Referral_Program&utm_source=badge)
