import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*

val appMainClass: String by project

plugins {
    kotlin("jvm") version "1.5.20"
    application
}

group = "me.itdog"
version = "0.0.1-SNAPSHOT"

application {
    mainClass.set(appMainClass)
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("org.telegram:telegrambots:5.3.0")
    implementation("commons-cli:commons-cli:1.4")
    implementation("com.squareup.okhttp3:okhttp:4.9.1")
    implementation("com.google.code.gson:gson:2.8.7")
    implementation("org.junit.jupiter:junit-jupiter:5.7.0")
    implementation("com.google.guava:guava:30.1.1-jre")
    implementation("org.jsoup:jsoup:1.14.2")
    implementation("redis.clients:jedis:3.7.0")
    implementation("ch.qos.logback:logback-classic:1.2.11")
    testImplementation("org.jetbrains.kotlin:kotlin-test")
    runtimeOnly("org.jetbrains.kotlin:kotlin-reflect")
}

tasks.create("createBuildProperties") {
    doLast {
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss").format(Date())
        val version = project.version.toString()

        File("$buildDir/resources/main").mkdirs()
        val properties = Properties()
        properties["version"] = version
        properties["timestamp"] = timestamp
        properties.store(
            FileWriter("$buildDir/resources/main/build.properties"),
            "Generated build properties."
        )
    }
}

tasks.jar {
    dependsOn("createBuildProperties")

    manifest {
        attributes(
            "Main-Class" to appMainClass
        )
    }

    eachFile {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

    from(
        configurations.compileClasspath.get().map {
            if (it.isDirectory) it else zipTree(it)
        }
    )

    archiveFileName.set("${archiveBaseName.get()}.${archiveExtension.get()}")

    exclude("META-INF/*.RSA", "META-INF/*.SF", "META-INF/*.DSA")
}

tasks.test {
    useJUnitPlatform()
}
