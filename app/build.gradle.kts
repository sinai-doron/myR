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

        // doron-backend (sign-in and receipt PDFs): staging at apps.neonsheep.dev. For a backend
        // running on this computer set backend.url=http://localhost:7300 in local.properties and
        // run `adb reverse tcp:7300 tcp:7300`.
        val backendUrl = localProps.getProperty("backend.url", "https://apps.neonsheep.dev")
        buildConfigField("String", "BACKEND_URL", "\"${backendUrl.trimEnd('/')}\"")
        // The backend's web OAuth client: the audience of the Google ID token. Not a secret.
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"381579861019-jdv1n9tsqhlh40iius7liep7p6co8cun.apps.googleusercontent.com\"",
        )
    }

    // One fixed debug key, so installing from any computer updates the app in place instead of
    // requiring an uninstall (which wipes captured receipts). The key stays out of this public
    // repo: point signing.storeFile in local.properties at it; without it the machine's default
    // debug key is used.
    signingConfigs {
        // The Play upload key. Play App Signing holds the key users' installs are signed with;
        // this one only proves uploads come from us, and Google can reset it if it is lost.
        // It lives outside this public repo, in GCP Secret Manager and the owner's Strongbox:
        // scripts/bundle-release.sh passes it as RELEASE_* environment variables for one build;
        // release.* in local.properties works too.
        val release = { key: String ->
            localProps.getProperty("release.$key") ?: System.getenv("RELEASE_${key.uppercase()}")
        }
        release("storeFile")?.let { path ->
            create("release") {
                storeFile = file(path)
                storePassword = release("storePassword")
                keyAlias = release("keyAlias") ?: "upload"
                keyPassword = release("keyPassword") ?: release("storePassword")
            }
        }
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Unsigned without the upload key, so a release can't be built by accident with a
            // debug key; the build says so rather than producing something Play would refuse.
            signingConfig = signingConfigs.findByName("release")
            // Releases always talk to the hosted backend, whatever local.properties says.
            buildConfigField("String", "BACKEND_URL", "\"https://apps.neonsheep.dev\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
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

    // Sign-in with Google through doron-backend
    implementation(libs.credentials)
    implementation(libs.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.datastore)
    implementation(libs.datastore.tink)
    implementation(libs.tink.android)
    implementation(libs.okhttp)
    // Play services auth pulls in an old Fragment; ActivityResult APIs need 1.3+ (lint, release)
    implementation(libs.fragment)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.coroutines.test)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
