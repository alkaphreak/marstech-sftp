import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("org.springframework.boot") version "3.2.1"
    id("io.spring.dependency-management") version "1.1.4"
    id("org.graalvm.buildtools.native") version "0.9.28"
    kotlin("jvm") version "1.9.21"
    kotlin("plugin.spring") version "1.9.21"
    kotlin("plugin.noarg") version "1.9.21"
}

group = "fr.marstech"
version = "0.0.1-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.VERSION_17
}

configurations {
    compileOnly {
        extendsFrom(configurations.annotationProcessor.get())
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // https://mvnrepository.com/artifact/io.github.oshai/kotlin-logging-jvm
    implementation("io.github.oshai:kotlin-logging-jvm:6.0.1")

    implementation("com.github.mwiede:jsch:0.2.13")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    implementation("org.springframework.boot:spring-boot-starter-test")
    implementation("org.springframework.boot:spring-boot-testcontainers")
    implementation("io.kotest:kotest-runner-junit5-jvm:5.7.2")

    // https://mvnrepository.com/artifact/io.kotest.extensions/kotest-extensions-spring
    implementation("io.kotest.extensions:kotest-extensions-spring:1.1.3")

    implementation("redis.clients:jedis:5.1.0")

    // https://mvnrepository.com/artifact/com.google.guava/guava
    implementation("com.google.guava:guava:32.1.3-jre")

    implementation("com.github.docker-java:docker-java:3.3.4")
    implementation("com.github.docker-java:docker-java-transport-httpclient5:3.3.4")

    implementation("javax.ws.rs:javax.ws.rs-api:2.1.1")
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict"
        jvmTarget = "17"
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}
