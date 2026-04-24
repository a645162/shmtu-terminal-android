plugins {
    alias(libs.plugins.android.library)
}

val jdkVersion: JavaVersion = rootProject.extra["jdkVersion"] as JavaVersion
val ndkVersionStr: String = rootProject.extra["ndkVersion"] as String
val sdkVersion: Int = rootProject.extra["sdkVersion"] as Int

println("Using JDK $jdkVersion, NDK $ndkVersionStr, SDK $sdkVersion")

android {
    namespace = "cn.edu.shmtu.cas.ocr"
    compileSdk = sdkVersion

    defaultConfig {
        minSdk = 21
        consumerProguardFiles("consumer-rules.pro")
        externalNativeBuild {
            cmake {
                cppFlags.add("")
                abiFilters += listOf("x86", "x86_64", "armeabi-v7a", "arm64-v8a")
            }
        }
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

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }
    ndkVersion = ndkVersionStr
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

    implementation(libs.okhttp)

    implementation(libs.kotlinx.coroutines.android)
}
