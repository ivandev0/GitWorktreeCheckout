plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.8.21"
    id("org.jetbrains.intellij") version "1.14.2"
}

group = "org.kylchik"
version = "1.0-SNAPSHOT"

private val ideVersion = "222.4554.10"

repositories {
    mavenCentral()

    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")

    // The following 4 repositories are required for `vcs-test-framework`
    // See https://intellij-support.jetbrains.com/hc/en-us/community/posts/8188184803730-Plugin-Testing-Git-Integration
    // serviceMessages
    maven("https://cache-redirector.jetbrains.com/download.jetbrains.com/teamcity-repository")
    // pgp-verifier
    maven("https://cache-redirector.jetbrains.com/download-pgp-verifier")
    // Grazie
    maven("https://cache-redirector.jetbrains.com/packages.jetbrains.team/maven/p/grazi/grazie-platform-public")
    // Ktor
    maven("https://maven.pkg.jetbrains.space/public/p/ktor/eap")
}

dependencies {
    testImplementation("com.jetbrains.intellij.platform:vcs-test-framework:$ideVersion")
    testImplementation("com.jetbrains.intellij.vcs:git:$ideVersion")
    testImplementation("org.assertj:assertj-core:3.11.1")
}

// Configure Gradle IntelliJ Plugin
// Read more: https://plugins.jetbrains.com/docs/intellij/tools-gradle-intellij-plugin.html
intellij {
    version.set(ideVersion)
    type.set("IC") // Target IDE Platform

    plugins.set(listOf("Git4Idea"))
}

tasks {
    // Set the JVM compatibility versions
    withType<JavaCompile> {
        sourceCompatibility = "17"
        targetCompatibility = "17"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        kotlinOptions.jvmTarget = "17"
    }

    patchPluginXml {
        sinceBuild.set("222")
        untilBuild.set("232.*")
    }

    signPlugin {
        certificateChain.set(System.getenv("CERTIFICATE_CHAIN"))
        privateKey.set(System.getenv("PRIVATE_KEY"))
        password.set(System.getenv("PRIVATE_KEY_PASSWORD"))
    }

    publishPlugin {
        token.set(System.getenv("PUBLISH_TOKEN"))
    }
}
