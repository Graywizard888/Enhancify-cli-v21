import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin)
    alias(libs.plugins.shadow)
    application
    `maven-publish`
    signing
}

group = "app.revanced"

// ===== AUTO VERSION =====
version = System.getenv("VERSION") 
    ?: project.findProperty("version")?.toString()
    ?: "1.0.0-SNAPSHOT"

// Log version on configuration
println("📌 Project version: $version")

val githubUser = project.findProperty("gpr.user") as String? ?: System.getenv("GITHUB_ACTOR")
val githubToken = project.findProperty("gpr.key") as String? ?: System.getenv("GITHUB_TOKEN")

application {
    mainClass = "app.revanced.cli.command.MainCommandKt"
}

repositories {
    mavenCentral()
    google()
    maven {
        url = uri("https://maven.pkg.github.com/inotia00/registry")
        credentials {
            username = githubUser
            password = githubToken
        }
    }
    maven {
        url = uri("https://maven.pkg.github.com/revanced/registry")
        credentials {
            username = githubUser
            password = githubToken
        }
    }
}

dependencies {
    implementation(libs.revanced.patcher)
    implementation(libs.revanced.library)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.picocli)
    implementation(libs.gson)
    testImplementation(libs.kotlin.test)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}

tasks {
    test {
        useJUnitPlatform()
        testLogging {
            events("PASSED", "SKIPPED", "FAILED")
        }
    }

    processResources {
        expand("projectVersion" to project.version)
    }

    shadowJar {
        archiveBaseName.set("revanced-cli")
        archiveVersion.set(project.version.toString())
        archiveClassifier.set("")
        
        exclude("/prebuilt/linux/aapt", "/prebuilt/windows/aapt.exe", "/prebuilt/*/aapt_*")
        minimize {
            exclude(dependency("org.bouncycastle:.*"))
            exclude(dependency("app.revanced:revanced-patcher"))
        }
    }

    build {
        dependsOn(shadowJar)
    }

    publish {
        dependsOn(shadowJar)
    }
}

publishing {
    repositories {
        mavenLocal()
    }

    publications {
        create<MavenPublication>("revanced-cli-publication") {
            from(components["java"])
            groupId = project.group.toString()
            artifactId = "revanced-cli"
            version = project.version.toString()
        }
    }
}

signing {
    isRequired = false
    if (System.getenv("CI") == null) {
        useGpgCmd()
        sign(publishing.publications["revanced-cli-publication"])
    }
}
