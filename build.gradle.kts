plugins {
    java
    application
}

group = "me.yuhan8954.slipstream"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.zxing:javase:3.5.4")
}

application {
    mainClass.set("me.yuhan8954.slipstream.Slipstream")
}

val slipstreamEnvironment = file(".env")
    .takeIf { it.isFile }
    ?.readLines()
    ?.mapNotNull { line ->
        line.trim()
            .takeUnless { it.isEmpty() || it.startsWith("#") }
            ?.split("=", limit = 2)
            ?.takeIf { it.size == 2 }
            ?.let { (key, value) -> key.trim() to value.trim() }
    }
    ?.toMap()
    .orEmpty()

tasks.named<JavaExec>("run") {
    listOf("SLIPSTREAM_KEYSTORE", "SLIPSTREAM_KEYSTORE_PASSWORD").forEach { name ->
        slipstreamEnvironment[name]?.let { value -> environment(name, value) }
    }
}
