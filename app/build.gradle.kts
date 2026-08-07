import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

/**
 * Release signing is driven by environment variables so CI can inject a keystore from
 * repository secrets. When they are absent (a normal local build, or a fork without secrets)
 * we fall back to the debug key so `assembleRelease` still produces an installable APK
 * instead of failing.
 */
val keystoreFile: File? = System.getenv("APPLOCK_KEYSTORE_PATH")
    ?.takeIf { it.isNotBlank() }
    ?.let(::File)
    ?.takeIf(File::exists)

android {
    namespace = "com.radley.applock"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.radley.applock"
        minSdk = 29
        targetSdk = 36
        versionCode = 3
        versionName = "1.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (keystoreFile != null) {
            create("release") {
                storeFile = keystoreFile
                storePassword = System.getenv("APPLOCK_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("APPLOCK_KEY_ALIAS")
                keyPassword = System.getenv("APPLOCK_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = if (keystoreFile != null) {
                signingConfigs.getByName("release")
            } else {
                logger.lifecycle("APPLOCK_KEYSTORE_PATH not set - signing release with the debug key.")
                signingConfigs.getByName("debug")
            }
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

    packaging {
        resources.excludes += setOf(
            "/META-INF/{AL2.0,LGPL2.1}",
            "/META-INF/DEPENDENCIES",
        )
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // The release build must not ship with a lint error, but an offline CI runner
        // should not fail the whole pipeline over a dependency-update notice.
        warningsAsErrors = false
        abortOnError = true
        disable += setOf("GradleDependency", "NewerVersionAvailable", "ObsoleteLintCustomCheck")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    // Pinned explicitly: androidx.biometric 1.1.0 still declares fragment 1.2.5, which is
    // far behind what activity-compose needs. Declaring it here keeps the version honest.
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.service)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.biometric)

    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
