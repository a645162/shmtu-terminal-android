plugins {
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

dependencies {
    implementation(libs.jsoup)
    implementation(libs.okhttp)
}
