plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val mozComponentsVersion = "150.0.2"

android {
    namespace = "com.mangodevelopers.vastbrowser.tv"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.mangodevelopers.vastbrowser.tv"
        minSdk = 26
        targetSdk = 34
        versionCode = 2
        versionName = "0.3.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = false
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Mozilla Android Components — GeckoEngine
    implementation("org.mozilla.components:browser-engine-gecko:$mozComponentsVersion")
    implementation("org.mozilla.components:browser-state:$mozComponentsVersion")
    implementation("org.mozilla.components:concept-engine:$mozComponentsVersion")
    implementation("org.mozilla.components:feature-session:$mozComponentsVersion")
    implementation("org.mozilla.components:support-base:$mozComponentsVersion")
    implementation("org.mozilla.components:support-ktx:$mozComponentsVersion")
    implementation("org.mozilla.components:support-utils:$mozComponentsVersion")
    implementation("org.mozilla.components:browser-domains:$mozComponentsVersion")
    implementation("org.mozilla.components:ui-colors:$mozComponentsVersion")
    implementation("org.mozilla.components:ui-icons:$mozComponentsVersion")

    // AndroidX
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.0")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.leanback:leanback:1.0.0")
    implementation("com.google.android.material:material:1.12.0")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
}
