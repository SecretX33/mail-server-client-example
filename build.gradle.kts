import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    id("com.github.johnrengelman.shadow") version "8.1.1"
    kotlin("jvm") version "1.9.10"
    application
}

group = "com.github.secretx33"
version = "0.1-SNAPSHOT"

val javaVersion = 17

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib-jdk8"))
    implementation(kotlin("reflect"))
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation(platform("com.fasterxml.jackson:jackson-bom:2.15.2"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-xml")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("com.github.davidmoten:subethasmtp:6.0.7")
    implementation("com.squareup.okhttp3:okhttp:4.11.0")
    implementation("io.github.secretx33:path-matching-resource-pattern-resolver:0.1")
}

tasks.test { useJUnitPlatform() }

tasks.jar { enabled = false }

artifacts.archives(tasks.shadowJar)

tasks.shadowJar {
    archiveFileName.set("${rootProject.name}.jar")
}

tasks.withType<JavaCompile> {
    options.apply {
        release.set(javaVersion)
        options.encoding = "UTF-8"
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf("-Xjsr305=strict", "-Xjvm-default=all")
        jvmTarget = javaVersion.toString()
    }
}

val mainClassName = "com.github.secretx33.mailexample.ServerKt"

application {
    mainClass.set(mainClassName)
}