import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    // Firebase plugins are applied below only when google-services.json is present, so the project
    // still builds before the JSON is added. See the apply block after the android { } config.
}

// Firebase (Analytics, Crashlytics, Cloud Messaging) turns on only once google-services.json is dropped into app/.
// Until then the plugins are skipped and the app builds and runs normally (Firebase init is also guarded at runtime).
val hasGoogleServices = file("google-services.json").exists() ||
    file("src/live/google-services.json").exists() || file("src/uat/google-services.json").exists()
if (hasGoogleServices) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
    apply(plugin = libs.plugins.firebase.crashlytics.get().pluginId)
}

// UAT pins: server key, Let's Encrypt YR1 intermediate, ISRG Root YR, ISRG Root X1/X2 (backups so a renewal can't lock users out).
val uatPins = listOf(
    "sha256/BGtca6XtWKJFYL86b+pZTiqV9F3ts7AkGwNoi1beSKc=",
    "sha256/LoMHBotttiDko50Gi13uXW71eIy7LAttI+rYT8wXF4w=",
    "sha256/fk6IOKit1ild5647BH06ujSIq5XbCgqlbYl6ANhhi88=",
    "sha256/C5+lpZ7tcVwmwQIMcRtPbsQtWLABXhQzejna0wHFr8M=",
    "sha256/diGVwiVYbubAI3RW4hB9xU8e/CH2GnkuvVFZE8zmgzI=",
).joinToString(",")

// TODO(live): set when the backend team shares the Live URL and its certificate pins.
val liveBaseUrl = "https://live-url-not-set.invalid/app/api/v1/"
val liveHost = "live-url-not-set.invalid"
val livePins = ""
// The app lock must be on in every Live build (checked below). UAT has it off until staging.
val liveAppLock = true

android {
    namespace = "com.asl.wallet"
    compileSdk { version = release(37) }

    defaultConfig {
        // Ships to Play under the original package so existing users receive it as an update (user, 11 Nov 2026).
        applicationId = "com.asl.wallet"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "2.0.0-m6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }
    }

    flavorDimensions += "env"
    productFlavors {
        create("uat") {
            dimension = "env"
            // No applicationId suffix: UAT shares the live package com.asl.wallet so the single
            // google-services.json (registered for com.asl.wallet) covers both builds. The UAT build stays
            // distinguishable by its name, -uat version suffix and debuggable flag; it just can't be installed
            // alongside the live app. To run them side by side instead, register com.asl.wallet.uat in Firebase
            // and restore `applicationIdSuffix = ".uat"`.
            versionNameSuffix = "-uat"
            resValue("string", "app_name", "ASL Wallet UAT")
            buildConfigField("String", "BASE_URL", "\"https://www.testproject.co.in/app/api/v1/\"")
            buildConfigField("String", "SUPPORT_PHONE", "\"9733815791\"")
            buildConfigField("String", "PIN_HOST", "\"www.testproject.co.in\"")
            buildConfigField("String", "PINS", "\"$uatPins\"")
            buildConfigField("boolean", "ALLOW_EMULATOR", "true")
            buildConfigField("boolean", "ALLOW_INTEGRITY_BYPASS", "true")
            // App lock (fingerprint / screen lock on open and after idle) is off in UAT until staging (user, 7 Nov 2026).
            buildConfigField("boolean", "APP_LOCK_ENABLED", "false")
            // PayU is on in UAT. UAT debug answers Add Money with the demo (app/src/uatDebug/.../demo) until the backend's
            // payments/payu APIs are on UAT; UAT release calls the real backend.
            buildConfigField("boolean", "PAYU_ENABLED", "true")
            // PayU LIVE environment in UAT (user, 9 Nov 2026): the UAT backend signs with ASL's live PayU key, so a
            // completed payment here moves real money. Set back to "false" when UAT switches to PayU test credentials.
            buildConfigField("boolean", "PAYU_PRODUCTION", "true")
        }
        create("live") {
            dimension = "env"
            resValue("string", "app_name", "ASL Wallet")
            buildConfigField("String", "BASE_URL", "\"$liveBaseUrl\"")
            buildConfigField("String", "SUPPORT_PHONE", "\"9733815791\"")
            buildConfigField("String", "PIN_HOST", "\"$liveHost\"")
            buildConfigField("String", "PINS", "\"$livePins\"")
            buildConfigField("boolean", "ALLOW_EMULATOR", "false")
            buildConfigField("boolean", "ALLOW_INTEGRITY_BYPASS", "false")
            buildConfigField("boolean", "APP_LOCK_ENABLED", "$liveAppLock")
            buildConfigField("boolean", "PAYU_ENABLED", "false")
            buildConfigField("boolean", "PAYU_PRODUCTION", "true")
        }
    }

    // Release signing: the keystore and its passwords come from the environment at build time and are never
    // written into the project (ASL_KEYSTORE, ASL_KEYSTORE_PASSWORD, ASL_KEY_ALIAS, ASL_KEY_PASSWORD).
    val releaseKeystore = System.getenv("ASL_KEYSTORE")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (releaseKeystore != null) create("release") {
            storeFile = file(releaseKeystore)
            storePassword = System.getenv("ASL_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ASL_KEY_ALIAS")
            keyPassword = System.getenv("ASL_KEY_PASSWORD")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
        resValues = true
    }
    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}", "/META-INF/*.kotlin_module", "DebugProbesKt.bin")
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        warningsAsErrors = false
    }
}

// Add Money demo (UAT debug only): a PayU *test* key and salt from local.properties, which is never committed.
// Without them the demo uses a simulated PayU screen. No other build gets these fields.
val localProps = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}
androidComponents {
    onVariants(selector().withName("uatDebug")) { v ->
        fun quoted(name: String) = "\"" + localProps.getProperty(name, "").trim().replace("\"", "") + "\""
        v.buildConfigFields?.put("PAYU_TEST_KEY", com.android.build.api.variant.BuildConfigField("String", quoted("payu.test.key"), "PayU TEST key (local.properties)"))
        v.buildConfigFields?.put("PAYU_TEST_SALT", com.android.build.api.variant.BuildConfigField("String", quoted("payu.test.salt"), "PayU TEST salt (local.properties)"))
    }
}

// Never ship a Live build without its real URL and pins.
tasks.configureEach {
    if (name.matches(Regex("(assemble|bundle|package)LiveRelease"))) {
        doFirst {
            if (!liveAppLock) throw GradleException("Live builds must have the app lock on (liveAppLock = true)")
            if (liveBaseUrl.contains(".invalid") || livePins.isBlank()) {
                throw GradleException("Live base URL / certificate pins are not set in app/build.gradle.kts")
            }
        }
    }
}

// Declared up front so the UAT-debug-only dependency below has a configuration to go in.
val uatDebugImplementation by configurations.creating

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:network"))
    implementation(project(":core:security"))
    implementation(project(":feature:auth"))
    implementation(project(":feature:home"))
    implementation(project(":feature:profile"))
    implementation(project(":feature:aeps"))
    implementation(project(":feature:wallet"))
    implementation(project(":feature:recharge"))
    implementation(project(":feature:bbps"))
    implementation(project(":feature:dmt"))
    implementation(project(":feature:reports"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    ksp(libs.hilt.compiler)

    // Firebase: Analytics, Crashlytics, Cloud Messaging (push). Versions from the BoM.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)
    // Google Play In-App Update (forced/immediate update gate).
    implementation(libs.play.app.update)
    implementation(libs.play.app.update.ktx)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // API inspector for real-device testing: UAT debug builds only. Release and live builds contain no Chucker code.
    uatDebugImplementation(libs.chucker)

    testImplementation(project(":core:testing"))
}
