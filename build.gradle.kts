plugins {
    id("com.gradleup.shadow") version "8.3.9"
    id("io.micronaut.graalvm") version "4.4.0"
    id("java")
    id("application")
}

version = "0.1"
group = "owpk"

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor("info.picocli:picocli-codegen:4.7.7")
    implementation("info.picocli:picocli:4.7.7")
}

application {
    mainClass = "owpk.JlogfmtCommand"
}

java {
    sourceCompatibility = JavaVersion.toVersion("25")
    targetCompatibility = JavaVersion.toVersion("25")
}

graalvmNative {
    binaries {
        named("main") {
            imageName = "jlogfmt"
            buildArgs.add("--verbose")
            buildArgs.add("-Os")
            buildArgs.add("--static-nolibc")
            buildArgs.add("-H:+PrintAnalysisCallTree")
            buildArgs.add("-H:+PrintImageObjectTree")
            buildArgs.add("--initialize-at-build-time=picocli")
            buildArgs.add("-H:+UseSerialGC")
        }
    }
    metadataRepository {
        enabled = true      // use GraalVM's reachability metadata repository
    }
}

graalvmNative.toolchainDetection = true