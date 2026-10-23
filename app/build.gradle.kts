import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

val akagiVersionName: String = providers.gradleProperty("akagiVersionName").get()

// major.minor.patch -> MMmmpp, so every release tag yields a strictly increasing versionCode.
fun versionCodeOf(versionName: String): Int {
    val parts = versionName.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val (major, minor, patch) = (parts + listOf(0, 0, 0)).take(3)
    return maxOf(1, major * 10_000 + minor * 100 + patch)
}

// Release signing comes from env vars (CI) or ~/.akagi/signing/keystore.properties (local). Never from the repo.
val localSigning = Properties().apply {
    val file = File(System.getProperty("user.home"), ".akagi/signing/keystore.properties")
    if (file.exists()) file.inputStream().use(::load)
}

fun signingValue(envKey: String, propertyKey: String): String? =
    System.getenv(envKey)?.takeIf { it.isNotBlank() } ?: localSigning.getProperty(propertyKey)

android {
    namespace = "io.github.z0orb.akagi"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.z0orb.akagi"
        minSdk = 29
        targetSdk = 36
        versionCode = versionCodeOf(akagiVersionName)
        versionName = akagiVersionName

        ndk {
            // Every Android 10+ phone worth supporting is arm64; keeps the yt-dlp/Python/ffmpeg payload small.
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        create("release") {
            signingValue("AKAGI_KEYSTORE_PATH", "storeFile")?.let { path ->
                storeFile = file(path)
                storePassword = signingValue("AKAGI_KEYSTORE_PASSWORD", "storePassword")
                keyAlias = signingValue("AKAGI_KEY_ALIAS", "keyAlias")
                keyPassword = signingValue("AKAGI_KEY_PASSWORD", "keyPassword")
                storeType = "pkcs12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfigs.getByName("release").takeIf { it.storeFile != null }?.let { signingConfig = it }
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
        // youtubedl-android runs Python/ffmpeg/QuickJS as executables, which must be extracted to disk.
        jniLibs.useLegacyPackaging = true
    }

    lint {
        abortOnError = true
        // Version-freshness checks are informational; versions are reviewed deliberately per phase.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable", "OldTargetApi")
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    implementation(libs.youtubedl.library)
    implementation(libs.youtubedl.ffmpeg)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)
    implementation(libs.reorderable)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.org.json)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}
