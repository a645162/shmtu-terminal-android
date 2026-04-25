plugins {
    id("application")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

val jdkVersion: JavaVersion = rootProject.extra["jdkVersion"] as JavaVersion

application {
    mainClass.set("cn.edu.shmtu.cas.demo.MainKt")
}

java {
    sourceCompatibility = jdkVersion
    targetCompatibility = jdkVersion
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(jdkVersion.toString()))
    }
}

dependencies {
    implementation(project(":cas_lib"))
}
