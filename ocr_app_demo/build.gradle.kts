plugins {
    alias(libs.plugins.android.application)
}

val jdkVersion: JavaVersion = rootProject.extra["jdkVersion"] as JavaVersion
val sdkVersion: Int = rootProject.extra["sdkVersion"] as Int
val buildToolsVersionStr: String = rootProject.extra["buildToolsVersion"] as String
val appVersionCode: Int = rootProject.extra["appVersionCode"] as Int
val appVersionName: String = rootProject.extra["appVersionName"] as String

println("Using JDK $jdkVersion, SDK $sdkVersion, Version $appVersionName($appVersionCode)")

android {
    namespace = "com.khm.shmtu.cas.ocr.demo"
    compileSdk = sdkVersion
    buildToolsVersion = buildToolsVersionStr

    defaultConfig {
        applicationId = "com.khm.shmtu.cas.ocr.demo"
        minSdk = 23
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
        isCoreLibraryDesugaringEnabled = true
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

    implementation(libs.okhttp)

    implementation(libs.kotlinx.coroutines.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    implementation(project(":shmtu_ocr"))
    implementation(libs.shmtu.cas.android)
}
