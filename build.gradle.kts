import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java
    kotlin("jvm") version "1.5.30"
    id("io.gitlab.arturbosch.detekt").version("1.18.0")
    application
}

group = "cc.makin"
version = "0.1"

application {
    mainClass.set("cc.makin.coinmonitor.cli.CoinMonitorKt")
}

repositories {
    mavenCentral()
}

detekt {
    config = files("detekt.yml")
}

dependencies {
    detektPlugins("pl.setblack:kure-potlin:0.5.0")

    implementation("org.jetbrains.kotlin:kotlin-stdlib:1.5.30")
    implementation("io.arrow-kt:arrow-core:1.0.1")
    implementation("org.jetbrains.exposed", "exposed-core", "0.31.1")
    implementation("org.jetbrains.exposed", "exposed-dao", "0.31.1")
    implementation("org.jetbrains.exposed", "exposed-jdbc", "0.31.1")
    implementation("org.slf4j:slf4j-api:1.7.32")
    implementation("org.slf4j:slf4j-simple:1.7.32")
    implementation("dev.kord:kord-core:0.8.0-M7")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("io.ktor:ktor-client-gson:1.6.4")

    testImplementation("io.ktor:ktor-client-mock:1.6.4")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}