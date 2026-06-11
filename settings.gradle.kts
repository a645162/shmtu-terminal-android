pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/jcenter") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://jitpack.io") }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)
    repositories {
        google()
        maven { url = uri("https://maven.aliyun.com/repository/central") }
        maven { url = uri("https://maven.aliyun.com/repository/jcenter") }
        maven { url = uri("https://maven.aliyun.com/repository/google") }
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        maven { url = uri("https://jitpack.io") }
        exclusiveContent {
            forRepository {
                mavenCentral()
            }
            filter {
                includeModule("org.jetbrains.kotlin", "kotlin-metadata-jvm")
            }
        }
        exclusiveContent {
            forRepository {
                mavenCentral()
            }
            filter {
                includeGroup("io.github.koalaplot")
            }
        }
        exclusiveContent {
            forRepository {
                mavenCentral()
            }
            filter {
                includeGroup("io.coil-kt")
            }
        }
    }
}

rootProject.name = "shmtu-terminal-android"

includeBuild("lib/shmtu-cas-kotlin") {
    // shmtu-cas-jvm artifact -> :cas_lib (保留:CLI 之外的子模块如果未来要单独引用,会用到)
    dependencySubstitution {
        substitute(module("cn.edu.shmtu.cas:shmtu-cas-jvm")).using(project(":cas_lib"))
        // shmtu-cas-android artifact -> :cas_android_lib (app / ocr_app_demo 主用此引用)
        substitute(module("cn.edu.shmtu.cas:shmtu-cas-android")).using(project(":cas_android_lib"))
    }
}

include(":app")

include(":ocr_app_demo")
include(":shmtu_ocr")
