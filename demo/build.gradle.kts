plugins {
    alias(libs.plugins.android.application)
}

val jdkVersion: JavaVersion = rootProject.extra["jdkVersion"] as JavaVersion
val sdkVersion: Int = rootProject.extra["sdkVersion"] as Int
val appVersionCode: Int = rootProject.extra["appVersionCode"] as Int
val appVersionName: String = rootProject.extra["appVersionName"] as String

println("Using JDK $jdkVersion, SDK $sdkVersion, Version $appVersionName($appVersionCode)")

android {
    namespace = "com.khm.shmtu.cas.ocr.demo"
    compileSdk = sdkVersion

    defaultConfig {
        applicationId = "com.khm.shmtu.cas.ocr.demo"
        minSdk = 21
        targetSdk = sdkVersion
        versionCode = appVersionCode
        versionName = appVersionName
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = jdkVersion
        targetCompatibility = jdkVersion
    }

    dependenciesInfo {
        includeInApk = true
        includeInBundle = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(jdkVersion.toString()))
    }
}

dependencies {
    implementation(libs.material)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)

    implementation(libs.kotlinx.coroutines.android)

    implementation(project(":shmtu_ocr"))
    implementation(project(":cas_lib"))
}
