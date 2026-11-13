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

tasks.named<JavaExec>("run") {
    environment(
        "SLIPSTREAM_KEYSTORE",
        providers.gradleProperty("slipstreamKeystore").getOrElse("keystore.p12")
    )
    environment(
        "SLIPSTREAM_KEYSTORE_PASSWORD",
        providers.gradleProperty("slipstreamKeystorePassword").getOrElse("change-this-password")
    )
}
