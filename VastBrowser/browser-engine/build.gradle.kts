plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val mozComponentsVersion = "150.0.2"

android {
    namespace = "com.mangodevelopers.vastbrowser.tv.engine"
    compileSdk = 36

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Mozilla Android Components
    implementation("org.mozilla.components:browser-engine-system:$mozComponentsVersion")
    implementation("org.mozilla.components:browser-engine-gecko:$mozComponentsVersion")
    implementation("org.mozilla.components:browser-state:$mozComponentsVersion")
    implementation("org.mozilla.components:concept-engine:$mozComponentsVersion")
    implementation("org.mozilla.components:support-base:$mozComponentsVersion")

    // Kotlin Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("androidx.core:core-ktx:1.15.0")
}
