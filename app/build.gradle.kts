plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

val jdkVersion: JavaVersion = rootProject.extra["jdkVersion"] as JavaVersion
val sdkVersion: Int = rootProject.extra["sdkVersion"] as Int
val buildToolsVersionStr: String = rootProject.extra["buildToolsVersion"] as String

println("Using JDK $jdkVersion, SDK $sdkVersion")

android {
    namespace = "cn.edu.shmtu.terminal.android"
    compileSdk = sdkVersion
    buildToolsVersion = buildToolsVersionStr

    defaultConfig {
        applicationId = "cn.edu.shmtu.terminal.android"
        minSdk = 28
        targetSdk = sdkVersion
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = jdkVersion
        targetCompatibility = jdkVersion
    }
    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(jdkVersion.toString()))
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    implementation(libs.hilt.android)
    ksp(libs.hilt.android.compiler)
    implementation(libs.hilt.navigation.compose)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.navigation.compose)

    implementation(libs.security.crypto)

    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.vico.compose.m3)

    implementation(libs.lifecycle.viewmodel.compose)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(project(":cas_lib"))
    implementation(project(":shmtu_ocr"))
}
