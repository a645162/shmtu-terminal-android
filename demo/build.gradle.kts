plugins {
    id("com.android.application")
}

val jdkVersion: JavaVersion = rootProject.extra["jdkVersion"] as JavaVersion

println("Using JDK $jdkVersion")

android {
    namespace = "com.khm.shmtu.cas.ocr.demo"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.khm.shmtu.cas.ocr.demo"
        minSdk = 21
        targetSdk = 37
        versionCode = 120
        versionName = "1.2.0"
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

    kotlin {
        compileOptions {
            sourceCompatibility = jdkVersion
            targetCompatibility = jdkVersion
        }
    }

    dependenciesInfo {
        includeInApk = true
        includeInBundle = true
    }
}

dependencies {
    implementation(libs.material)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)

    implementation(project(":shmtu_ocr"))
}
