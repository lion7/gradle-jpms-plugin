import org.gradle.api.tasks.wrapper.Wrapper.DistributionType.ALL
import java.io.ByteArrayOutputStream

plugins {
    `java-gradle-plugin`
    `kotlin-dsl`
    `maven-publish`
    id("com.gradle.plugin-publish") version "0.15.0"
    id("org.owasp.dependencycheck") version "6.2.2"
    id("com.github.ben-manes.versions") version "0.39.0"
}

group = "nl.leeuwit.gradle"
version = gitVersion()

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:1.5.20")
}

buildScan {
    termsOfServiceUrl = "https://gradle.com/terms-of-service"
    termsOfServiceAgree = "yes"
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    target {
        compilations.configureEach {
            kotlinOptions {
                freeCompilerArgs = listOf("-Xjsr305=strict")
                jvmTarget = "11"
            }
        }
    }
}

gradlePlugin {
    plugins {
        create("modulesPlugin") {
            id = "nl.leeuwit.gradle.jpms"
            displayName = "JPMS Gradle Plugin"
            description = "A Gradle plugin that adds support for the Java Platform Module System"
            implementationClass = "nl.leeuwit.gradle.jpms.JpmsPlugin"
        }
    }
}

pluginBundle {
    website = "https://github.com/lion7/gradle-jpms-plugin"
    vcsUrl = "https://github.com/lion7/gradle-jpms-plugin"
    tags = listOf("java", "jpms", "jigsaw", "modules", "modularity")
}

tasks {
    wrapper {
        distributionType = ALL
        gradleVersion = "7.1.1"
    }

    test {
        useJUnitPlatform()
    }
}

fun gitDescribe(): String = ByteArrayOutputStream().use { output ->
    exec {
        commandLine("git", "describe", "--long", "--match", "v[0-9]*")
        standardOutput = output
    }
    output.toByteArray().toString(Charsets.UTF_8)
}

fun gitVersion(): String = gitDescribe().substringAfter('v').substringBeforeLast('-').replace('-', '.')
