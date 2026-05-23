// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
}

extra["jdkVersion"] = JavaVersion.VERSION_21
extra["ndkVersion"] = "30.0.14904198"
extra["sdkVersion"] = 37
extra["buildToolsVersion"] = "37.0.0"
extra["appVersionCode"] = 130
extra["appVersionName"] = "1.3.0"

//fun isNonStable(version: String): Boolean {
//    val stableKeyword = listOf("RELEASE", "FINAL", "GA").any {
//        version.uppercase().contains(it)
//    }
//    val regex = "^[0-9,.v-]+(-r)?$".toRegex()
//    val isStable = stableKeyword || regex.matches(version)
//    return isStable.not()
//}
//
//allprojects {
//    apply<com.github.benmanes.gradle.versions.VersionsPlugin>()
//
//    tasks.named<DependencyUpdatesTask>("dependencyUpdates").configure {
//        // configure the task, for example wrt. resolution strategies
//
//        checkForGradleUpdate = true
//        outputFormatter = "txt"
//        outputDir = "build/dependencyUpdates"
//        reportfileName = "report"
//
//        rejectVersionIf {
//            isNonStable(candidate.version)
//        }
//    }
//}
