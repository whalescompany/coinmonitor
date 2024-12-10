plugins {
    java
    kotlin("jvm") version "2.1.0"
    application
    id("com.github.johnrengelman.shadow").version("8.1.1")
}

group = "cc.makin"
version = "0.1"

application {
    mainClass.set("cc.makin.coinmonitor.cli.CoinMonitorKt")
}

repositories {
    mavenCentral()
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.0")
    implementation("io.arrow-kt:arrow-core:1.0.1")
    implementation("org.jetbrains.exposed", "exposed-core", "0.31.1")
    implementation("org.jetbrains.exposed", "exposed-dao", "0.31.1")
    implementation("org.jetbrains.exposed", "exposed-jdbc", "0.31.1")
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("org.slf4j:slf4j-simple:2.0.16")
    implementation("dev.kord:kord-core:0.15.0")
    implementation("com.google.code.gson:gson:2.8.9")
    implementation("io.ktor:ktor-client-gson:1.6.4")
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.2.0")

    testImplementation("io.ktor:ktor-client-mock:1.6.4")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.8.1")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.8.1")
}

tasks.test {
    useJUnitPlatform()
}

tasks.wrapper {
    gradleVersion = "8.11.1"
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "cc.makin.coinmonitor.cli.CoinMonitorKt")
    }
}