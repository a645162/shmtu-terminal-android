plugins {
    alias(libs.plugins.kotlinx.serialization)
    id("java-library")
    alias(libs.plugins.jetbrains.kotlin.jvm)
}

val jdkVersion: JavaVersion = rootProject.extra["jdkVersion"] as JavaVersion

println("Using JDK $jdkVersion")

//java {
//    sourceCompatibility = JavaVersion.VERSION_11
//    targetCompatibility = JavaVersion.VERSION_11
//}
//kotlin {
//    compilerOptions {
//        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_11
//    }
//}

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
    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.serialization.json)
}
