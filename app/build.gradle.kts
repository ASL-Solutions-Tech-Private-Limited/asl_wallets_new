plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
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

android {
    namespace = "com.asl.wallet"
    compileSdk { version = release(37) }

    defaultConfig {
        applicationId = "com.asl.wallet.app"
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
            applicationIdSuffix = ".uat"
            versionNameSuffix = "-uat"
            resValue("string", "app_name", "ASL Wallet UAT")
            buildConfigField("String", "BASE_URL", "\"https://www.testproject.co.in/app/api/v1/\"")
            buildConfigField("String", "SUPPORT_PHONE", "\"9733815791\"")
            buildConfigField("String", "PIN_HOST", "\"www.testproject.co.in\"")
            buildConfigField("String", "PINS", "\"$uatPins\"")
            buildConfigField("boolean", "ALLOW_EMULATOR", "true")
            buildConfigField("boolean", "ALLOW_INTEGRITY_BYPASS", "true")
            // PayU stays off until the backend returns the CheckoutPro fields; "Pay" then shows "opens soon".
            buildConfigField("boolean", "PAYU_ENABLED", "false")
            buildConfigField("boolean", "PAYU_PRODUCTION", "false")
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
            buildConfigField("boolean", "PAYU_ENABLED", "false")
            buildConfigField("boolean", "PAYU_PRODUCTION", "true")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
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

// Never ship a Live build without its real URL and pins.
tasks.configureEach {
    if (name.matches(Regex("(assemble|bundle|package)LiveRelease"))) {
        doFirst {
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
    debugImplementation(libs.androidx.compose.ui.tooling)
    // API inspector for real-device testing: UAT debug builds only. Release and live builds contain no Chucker code.
    uatDebugImplementation(libs.chucker)

    testImplementation(project(":core:testing"))
}
