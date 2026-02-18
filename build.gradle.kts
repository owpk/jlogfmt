plugins {
    id("io.micronaut.application") version "4.6.1"
    id("com.gradleup.shadow") version "8.3.9"
    id("io.micronaut.graalvm") version "4.4.0"
}

version = "0.1"
group = "owpk"

repositories {
    mavenCentral()
}

dependencies {
    annotationProcessor("org.projectlombok:lombok")
    annotationProcessor("info.picocli:picocli-codegen")
    annotationProcessor("io.micronaut.serde:micronaut-serde-processor")
    implementation("info.picocli:picocli")
    implementation("io.micronaut.picocli:micronaut-picocli")
    implementation("io.micronaut.serde:micronaut-serde-jackson")
    compileOnly("org.projectlombok:lombok")
    runtimeOnly("ch.qos.logback:logback-classic")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    runtimeOnly("io.micronaut:micronaut-graal")    
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
        }
    }
    metadataRepository {
        enabled = true      // use GraalVM's reachability metadata repository
    }
}

graalvmNative.toolchainDetection = true

micronaut {
    testRuntime("junit5")
    processing {
        incremental(true)
        annotations("owpk.*")
    }
}

tasks.named<io.micronaut.gradle.docker.NativeImageDockerfile>("dockerfileNative") {
    jdkVersion = "25"
}