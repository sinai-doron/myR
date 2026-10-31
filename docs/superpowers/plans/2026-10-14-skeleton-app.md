# Skeleton App Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scaffold the Android project, add a splash screen, and build the CapturedListScreen with an empty state — a launchable shell for the SMS Receipt Capture app.

**Architecture:** Single-activity Compose app. MainActivity hosts a NavHost with one screen (CapturedListScreen). Splash screen via `core-splashscreen` library. Material 3 theming with dynamic color on supported devices.

**Tech Stack:** Kotlin 2.0.0, AGP 8.5.0, Gradle 8.7, Jetpack Compose (BOM 2024.06.00), Material 3, core-splashscreen 1.0.1, Navigation Compose 2.7.7

**Spec:** `docs/superpowers/specs/2026-10-14-android-sms-receipt-capture-design.md`

## Global Constraints

- `minSdk 26`, `compileSdk 35`, `targetSdk 35`
- Kotlin 2.0.0 with the Compose compiler plugin (`org.jetbrains.kotlin.plugin.compose`)
- Single module (`:app`), package `com.doron.myreceipts`
- No INTERNET permission, no network libraries
- Sideload only — debug signing, no Play Store config
- ANDROID_HOME is `/opt/homebrew/share/android-commandlinetools`
- Java 17 (OpenJDK via Homebrew)

## Review Focus

1. **Gradle sync on a fresh clone** — the `ANDROID_HOME` path is non-standard (`/opt/homebrew/share/android-commandlinetools`); `local.properties` must point to it or the env var must be set. Expect `./gradlew assembleDebug` to find the SDK without manual intervention.
2. **Splash screen on API 26–30 devices** — `core-splashscreen` applies a compat layer pre-Android 12. The splash theme must be set on the activity in the manifest (not just in code) or the compat path is skipped and a white flash appears.
3. **Empty state when receipt list is empty** — the screen must show a clear empty-state message, not a blank white screen or a loading spinner. Verified by a Compose UI test with an empty list.
4. **Dark mode** — Material 3 theme must define both light and dark color schemes. The empty-state screen must be readable in both. Verified visually or by running the Compose preview in dark mode.
5. **Back-button behavior on the single screen** — pressing back on CapturedListScreen should exit the app, not navigate to a blank screen. NavHost with a single destination handles this by default, but verify there's no extra back-stack entry from the splash or a redirect.

---

## File Structure

```
myR/
├── settings.gradle.kts
├── build.gradle.kts                          (root — plugin declarations only)
├── gradle.properties
├── local.properties                          (sdk.dir, gitignored)
├── .gitignore
├── gradle/
│   └── libs.versions.toml                    (version catalog)
├── app/
│   ├── build.gradle.kts                      (module build config)
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── res/
│   │   │   │   ├── values/
│   │   │   │   │   ├── strings.xml
│   │   │   │   │   ├── colors.xml
│   │   │   │   │   └── themes.xml           (splash theme)
│   │   │   │   ├── values-night/
│   │   │   │   │   └── themes.xml           (splash theme dark)
│   │   │   │   ├── mipmap-xxxhdpi/
│   │   │   │   │   └── ic_launcher.webp     (placeholder)
│   │   │   │   └── drawable/
│   │   │   │       └── ic_receipt.xml        (vector icon for splash + empty state)
│   │   │   └── java/com/doron/myreceipts/
│   │   │       ├── MainActivity.kt
│   │   │       └── ui/
│   │   │           ├── theme/
│   │   │           │   ├── Color.kt
│   │   │           │   ├── Theme.kt
│   │   │           │   └── Type.kt
│   │   │           └── screens/
│   │   │               └── CapturedListScreen.kt
│   │   └── androidTest/
│   │       └── java/com/doron/myreceipts/ui/screens/
│   │           └── CapturedListScreenTest.kt
```

---

### Task 1: Project Scaffold and Gradle Build

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts` (root)
- Create: `gradle.properties`
- Create: `local.properties`
- Create: `.gitignore`
- Create: `gradle/libs.versions.toml`
- Create: `app/build.gradle.kts`
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/res/values/strings.xml`
- Create: `app/src/main/java/com/doron/myreceipts/MainActivity.kt` (minimal placeholder)

**Interfaces:**
- Consumes: nothing (first task)
- Produces: a compilable Android project that `./gradlew assembleDebug` builds successfully

- [ ] **Step 1: Initialize git repository**

```bash
cd myR
git init
```

- [ ] **Step 2: Create `.gitignore`**

```gitignore
# Gradle
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar

# IDE
.idea/
*.iml
.DS_Store

# Local config
local.properties

# APK
*.apk
*.aab
```

- [ ] **Step 3: Install Gradle and generate the wrapper**

```bash
brew install gradle  # if not already installed
cd myR
gradle wrapper --gradle-version 8.7
```

This creates `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, and `gradle/wrapper/gradle-wrapper.properties`. All subsequent commands use `./gradlew`.

- [ ] **Step 4: Create `local.properties`**

```properties
sdk.dir=/opt/homebrew/share/android-commandlinetools
```

- [ ] **Step 5: Create `gradle/libs.versions.toml`**

```toml
[versions]
agp = "8.5.0"
kotlin = "2.0.0"
compose-bom = "2024.06.00"
core-splashscreen = "1.0.1"
activity-compose = "1.9.0"
navigation-compose = "2.7.7"
core-ktx = "1.13.1"
lifecycle-runtime-ktx = "2.8.2"
junit = "4.13.2"
ext-junit = "1.1.5"
espresso-core = "3.5.1"

[libraries]
compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "compose-bom" }
compose-ui = { group = "androidx.compose.ui", name = "ui" }
compose-ui-graphics = { group = "androidx.compose.ui", name = "ui-graphics" }
compose-material3 = { group = "androidx.compose.material3", name = "material3" }
compose-ui-tooling = { group = "androidx.compose.ui", name = "ui-tooling" }
compose-ui-tooling-preview = { group = "androidx.compose.ui", name = "ui-tooling-preview" }
compose-ui-test-junit4 = { group = "androidx.compose.ui", name = "ui-test-junit4" }
compose-ui-test-manifest = { group = "androidx.compose.ui", name = "ui-test-manifest" }
activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activity-compose" }
navigation-compose = { group = "androidx.navigation", name = "navigation-compose", version.ref = "navigation-compose" }
core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "core-splashscreen" }
core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "core-ktx" }
lifecycle-runtime-ktx = { group = "androidx.lifecycle", name = "lifecycle-runtime-ktx", version.ref = "lifecycle-runtime-ktx" }
junit = { group = "junit", name = "junit", version.ref = "junit" }
ext-junit = { group = "androidx.test.ext", name = "junit", version.ref = "ext-junit" }
espresso-core = { group = "androidx.test.espresso", name = "espresso-core", version.ref = "espresso-core" }

[plugins]
android-application = { id = "com.android.application", version.ref = "agp" }
kotlin-android = { id = "org.jetbrains.kotlin.android", version.ref = "kotlin" }
kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [ ] **Step 6: Create `settings.gradle.kts`**

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "MyReceipts"
include(":app")
```

- [ ] **Step 7: Create root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
```

- [ ] **Step 8: Create `gradle.properties`**

```properties
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
```

- [ ] **Step 9: Create `app/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.doron.myreceipts"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.doron.myreceipts"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.material3)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.activity.compose)
    implementation(libs.navigation.compose)
    implementation(libs.core.splashscreen)
    implementation(libs.core.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}
```

- [ ] **Step 10: Create `app/src/main/AndroidManifest.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <application
        android:allowBackup="false"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.MyReceipts.Splash">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:theme="@style/Theme.MyReceipts.Splash">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

- [ ] **Step 11: Create `app/src/main/res/values/strings.xml`**

```xml
<resources>
    <string name="app_name">My Receipts</string>
</resources>
```

- [ ] **Step 12: Create minimal `MainActivity.kt`**

File: `app/src/main/java/com/doron/myreceipts/MainActivity.kt`

```kotlin
package com.doron.myreceipts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContent {
            Text("My Receipts")
        }
    }
}
```

- [ ] **Step 13: Create placeholder launcher icon**

We need at least a mipmap entry for the manifest reference. Create a minimal adaptive icon setup:

File: `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_background"/>
    <foreground android:drawable="@drawable/ic_receipt"/>
</adaptive-icon>
```

File: `app/src/main/res/values/colors.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">#1B5E20</color>
    <color name="splash_background">#1B5E20</color>
</resources>
```

File: `app/src/main/res/drawable/ic_receipt.xml`
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="24"
    android:viewportHeight="24"
    android:tint="#FFFFFF">
    <path
        android:fillColor="@android:color/white"
        android:pathData="M18,17H6v-2h12v2zm0,-4H6v-2h12v2zm0,-4H6V7h12v2zM3,22l1.5,-1.5L6,22l1.5,-1.5L9,22l1.5,-1.5L12,22l1.5,-1.5L15,22l1.5,-1.5L18,22l1.5,-1.5L21,22V2l-1.5,1.5L18,2l-1.5,1.5L15,2l-1.5,1.5L12,2l-1.5,1.5L9,2L7.5,3.5L6,2L4.5,3.5L3,2v20z"/>
</vector>
```

- [ ] **Step 14: Create splash theme**

File: `app/src/main/res/values/themes.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.MyReceipts.Splash" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/splash_background</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/ic_receipt</item>
        <item name="postSplashScreenTheme">@style/Theme.MyReceipts</item>
    </style>

    <style name="Theme.MyReceipts" parent="android:Theme.Material.Light.NoActionBar" />
</resources>
```

File: `app/src/main/res/values-night/themes.xml`
```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.MyReceipts.Splash" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">@color/splash_background</item>
        <item name="windowSplashScreenAnimatedIcon">@drawable/ic_receipt</item>
        <item name="postSplashScreenTheme">@style/Theme.MyReceipts</item>
    </style>

    <style name="Theme.MyReceipts" parent="android:Theme.Material.NoActionBar" />
</resources>
```

- [ ] **Step 15: Build the project**

```bash
cd myR
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL. An APK at `app/build/outputs/apk/debug/app-debug.apk`.

- [ ] **Step 16: Commit**

```bash
git add .gitignore settings.gradle.kts build.gradle.kts gradle.properties gradle/ app/
git commit -m "feat: scaffold Android project with Gradle and splash screen config"
```

---

### Task 2: Material 3 Theme and Compose Shell

**Files:**
- Create: `app/src/main/java/com/doron/myreceipts/ui/theme/Color.kt`
- Create: `app/src/main/java/com/doron/myreceipts/ui/theme/Type.kt`
- Create: `app/src/main/java/com/doron/myreceipts/ui/theme/Theme.kt`
- Modify: `app/src/main/java/com/doron/myreceipts/MainActivity.kt`

**Interfaces:**
- Consumes: compilable project from Task 1
- Produces: `MyReceiptsTheme` composable (`@Composable (content: @Composable () -> Unit) -> Unit`) that provides Material 3 color scheme (light/dark) and typography. `MainActivity` uses it to wrap all screen content.

- [ ] **Step 1: Create `Color.kt`**

File: `app/src/main/java/com/doron/myreceipts/ui/theme/Color.kt`

```kotlin
package com.doron.myreceipts.ui.theme

import androidx.compose.ui.graphics.Color

val Green10 = Color(0xFF002106)
val Green20 = Color(0xFF00390F)
val Green30 = Color(0xFF005319)
val Green40 = Color(0xFF006E25)
val Green80 = Color(0xFF6EDD6E)
val Green90 = Color(0xFF8CFB8A)

val GreenGrey30 = Color(0xFF3B4F3E)
val GreenGrey50 = Color(0xFF5B6E5E)
val GreenGrey60 = Color(0xFF748776)
val GreenGrey80 = Color(0xFFB8CCB9)
val GreenGrey90 = Color(0xFFD4E8D5)
```

- [ ] **Step 2: Create `Type.kt`**

File: `app/src/main/java/com/doron/myreceipts/ui/theme/Type.kt`

```kotlin
package com.doron.myreceipts.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

val Typography = Typography(
    titleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp,
    ),
    bodyMedium = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.25.sp,
    ),
    labelSmall = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp,
    ),
)
```

- [ ] **Step 3: Create `Theme.kt`**

File: `app/src/main/java/com/doron/myreceipts/ui/theme/Theme.kt`

```kotlin
package com.doron.myreceipts.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = Green40,
    onPrimary = Green10,
    primaryContainer = Green90,
    onPrimaryContainer = Green10,
    secondary = GreenGrey50,
    onSecondary = GreenGrey90,
    secondaryContainer = GreenGrey90,
    onSecondaryContainer = GreenGrey30,
)

private val DarkColorScheme = darkColorScheme(
    primary = Green80,
    onPrimary = Green20,
    primaryContainer = Green30,
    onPrimaryContainer = Green90,
    secondary = GreenGrey80,
    onSecondary = GreenGrey30,
    secondaryContainer = GreenGrey30,
    onSecondaryContainer = GreenGrey90,
)

@Composable
fun MyReceiptsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
```

- [ ] **Step 4: Update `MainActivity.kt` to use the theme**

File: `app/src/main/java/com/doron/myreceipts/MainActivity.kt`

```kotlin
package com.doron.myreceipts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.doron.myreceipts.ui.theme.MyReceiptsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyReceiptsTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Text("My Receipts")
                }
            }
        }
    }
}
```

- [ ] **Step 5: Build and verify**

```bash
cd myR
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/doron/myreceipts/ui/theme/ app/src/main/java/com/doron/myreceipts/MainActivity.kt
git commit -m "feat: add Material 3 theme with light/dark and dynamic color support"
```

---

### Task 3: CapturedListScreen with Empty State and UI Test

**Files:**
- Create: `app/src/main/java/com/doron/myreceipts/ui/screens/CapturedListScreen.kt`
- Create: `app/src/androidTest/java/com/doron/myreceipts/ui/screens/CapturedListScreenTest.kt`
- Modify: `app/src/main/java/com/doron/myreceipts/MainActivity.kt`

**Interfaces:**
- Consumes: `MyReceiptsTheme` composable from Task 2
- Produces: `CapturedListScreen(receipts: List<CapturedReceipt>)` composable. `CapturedReceipt` is a simple data class (`link: String`, `senderLabel: String`, `timestamp: Long`, `clientId: String`) — the UI model, not the Room entity (which comes in a later plan). When `receipts` is empty, shows the empty state. When populated, shows a `LazyColumn` of receipt cards.

- [ ] **Step 1: Write the failing UI test for empty state**

File: `app/src/androidTest/java/com/doron/myreceipts/ui/screens/CapturedListScreenTest.kt`

```kotlin
package com.doron.myreceipts.ui.screens

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.doron.myreceipts.ui.theme.MyReceiptsTheme
import org.junit.Rule
import org.junit.Test

class CapturedListScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun emptyState_showsNoReceiptsMessage() {
        composeTestRule.setContent {
            MyReceiptsTheme(dynamicColor = false) {
                CapturedListScreen(receipts = emptyList())
            }
        }

        composeTestRule.onNodeWithText("No receipts captured yet").assertIsDisplayed()
        composeTestRule.onNodeWithText("Receipt SMS will appear here automatically").assertIsDisplayed()
    }

    @Test
    fun withReceipts_showsReceiptCards() {
        val receipts = listOf(
            CapturedReceipt(
                link = "https://shufersal.co.il/receipt/123",
                senderLabel = "Shufersal",
                timestamp = 1728900000000L,
                clientId = "test-id-1",
            ),
            CapturedReceipt(
                link = "https://pairzon.com/r/456",
                senderLabel = "OSHERAD",
                timestamp = 1728903600000L,
                clientId = "test-id-2",
            ),
        )

        composeTestRule.setContent {
            MyReceiptsTheme(dynamicColor = false) {
                CapturedListScreen(receipts = receipts)
            }
        }

        composeTestRule.onNodeWithText("Shufersal").assertIsDisplayed()
        composeTestRule.onNodeWithText("OSHERAD").assertIsDisplayed()
        composeTestRule.onNodeWithText("shufersal.co.il/receipt/123", substring = true).assertIsDisplayed()
    }
}
```

- [ ] **Step 2: Verify the test does not compile**

```bash
cd myR
./gradlew compileDebugAndroidTestKotlin 2>&1 | tail -5
```

Expected: compilation error — `CapturedListScreen` and `CapturedReceipt` are not defined.

- [ ] **Step 3: Implement `CapturedReceipt` data class and `CapturedListScreen`**

File: `app/src/main/java/com/doron/myreceipts/ui/screens/CapturedListScreen.kt`

```kotlin
package com.doron.myreceipts.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.doron.myreceipts.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CapturedReceipt(
    val link: String,
    val senderLabel: String,
    val timestamp: Long,
    val clientId: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapturedListScreen(receipts: List<CapturedReceipt>) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Receipts") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { innerPadding ->
        if (receipts.isEmpty()) {
            EmptyState(modifier = Modifier.padding(innerPadding))
        } else {
            ReceiptList(receipts = receipts, modifier = Modifier.padding(innerPadding))
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_receipt),
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No receipts captured yet",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Receipt SMS will appear here automatically",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReceiptList(receipts: List<CapturedReceipt>, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(receipts, key = { it.clientId }) { receipt ->
            ReceiptCard(receipt)
        }
    }
}

@Composable
private fun ReceiptCard(receipt: CapturedReceipt) {
    Card(
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = receipt.senderLabel,
                style = MaterialTheme.typography.titleLarge,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = receipt.link,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = formatTimestamp(receipt.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTimestamp(epochMillis: Long): String {
    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    return formatter.format(Date(epochMillis))
}
```

- [ ] **Step 4: Update `MainActivity.kt` to show `CapturedListScreen`**

File: `app/src/main/java/com/doron/myreceipts/MainActivity.kt`

```kotlin
package com.doron.myreceipts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.doron.myreceipts.ui.screens.CapturedListScreen
import com.doron.myreceipts.ui.theme.MyReceiptsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyReceiptsTheme {
                CapturedListScreen(receipts = emptyList())
            }
        }
    }
}
```

- [ ] **Step 5: Build and run the UI tests**

```bash
cd myR
./gradlew assembleDebug
```

Expected: BUILD SUCCESSFUL.

For the instrumented UI tests (requires a connected device or emulator):
```bash
./gradlew connectedDebugAndroidTest
```

If no device is available, verify compilation only:
```bash
./gradlew compileDebugAndroidTestKotlin
```

Expected: compilation succeeds. The actual UI tests run when sideloaded onto the Pixel 10 or an emulator.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/doron/myreceipts/ app/src/androidTest/
git commit -m "feat: add CapturedListScreen with empty state and receipt card list"
```
