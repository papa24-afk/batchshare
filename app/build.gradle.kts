plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.batchshare.wa"
    compileSdk = 35
    buildToolsVersion = "35.0.0"

    defaultConfig {
        applicationId = "com.batchshare.wa"
        minSdk = 26
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            // Supply credentials through Gradle properties or environment variables.
            // Never commit passwords or keystore files to source control.
            val releaseStorePath = providers.gradleProperty("BATCHSHARE_RELEASE_STORE_FILE")
                .orElse(providers.environmentVariable("BATCHSHARE_RELEASE_STORE_FILE"))
            val releaseStorePasswordValue = providers.gradleProperty("BATCHSHARE_RELEASE_STORE_PASSWORD")
                .orElse(providers.environmentVariable("BATCHSHARE_RELEASE_STORE_PASSWORD"))
            val releaseKeyAliasValue = providers.gradleProperty("BATCHSHARE_RELEASE_KEY_ALIAS")
                .orElse(providers.environmentVariable("BATCHSHARE_RELEASE_KEY_ALIAS"))
            val releaseKeyPasswordValue = providers.gradleProperty("BATCHSHARE_RELEASE_KEY_PASSWORD")
                .orElse(providers.environmentVariable("BATCHSHARE_RELEASE_KEY_PASSWORD"))

            if (releaseStorePath.isPresent &&
                releaseStorePasswordValue.isPresent &&
                releaseKeyAliasValue.isPresent &&
                releaseKeyPasswordValue.isPresent
            ) {
                storeFile = file(releaseStorePath.get())
                storePassword = releaseStorePasswordValue.get()
                keyAlias = releaseKeyAliasValue.get()
                keyPassword = releaseKeyPasswordValue.get()
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Developer's own distribution signing is optional for reproducible builds.
            // If credentials are supplied, sign with the configured release key.
            val releaseSigning = signingConfigs.getByName("release")
            if (releaseSigning.storeFile != null) {
                signingConfig = releaseSigning
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    lint {
        checkReleaseBuilds = false
        abortOnError = false
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
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.7.0")
    implementation("androidx.activity:activity-compose:1.8.2")

    val composeBom = platform("androidx.compose:compose-bom:2024.02.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("net.lingala.zip4j:zip4j:2.11.5")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
