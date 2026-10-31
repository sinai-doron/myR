import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.neonsheep.myreceipts"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.neonsheep.myreceipts"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // One fixed debug key, so installing from any computer updates the app in place instead of
    // requiring an uninstall (which wipes captured receipts). The key stays out of this public
    // repo: point signing.storeFile in local.properties at it; without it the machine's default
    // debug key is used.
    signingConfigs {
        getByName("debug") {
            localProps.getProperty("signing.storeFile")?.let { path ->
                storeFile = file(path)
                storePassword = localProps.getProperty("signing.storePassword", "android")
                keyAlias = localProps.getProperty("signing.keyAlias", "androiddebugkey")
                keyPassword = localProps.getProperty("signing.keyPassword", "android")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons.core)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.core.splashscreen)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.work.runtime.ktx)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
