import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.isFile) {
        file.inputStream().use { load(it) }
    }
}
val rawFeedbackRecipient = (
    localProperties.getProperty("feedback.recipient")
        ?: System.getenv("FEEDBACK_RECIPIENT")
        ?: ""
    ).trim()
val feedbackEndpoint = if (rawFeedbackRecipient.isNotEmpty()) {
    "https://formsubmit.co/$rawFeedbackRecipient"
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
} else {
    ""
}

val releaseStoreFilePath = (
    localProperties.getProperty("release.storeFile")
        ?: System.getenv("ANDROID_KEYSTORE_PATH")
        ?: ""
    ).trim()
val releaseStorePassword = (
    localProperties.getProperty("release.storePassword")
        ?: System.getenv("ANDROID_KEYSTORE_PASSWORD")
        ?: ""
    ).trim()
val releaseKeyAlias = (
    localProperties.getProperty("release.keyAlias")
        ?: System.getenv("ANDROID_KEY_ALIAS")
        ?: ""
    ).trim()
val releaseKeyPassword = (
    localProperties.getProperty("release.keyPassword")
        ?: System.getenv("ANDROID_KEY_PASSWORD")
        ?: ""
    ).trim()
val releaseKeystoreFile = if (releaseStoreFilePath.isNotBlank()) {
    rootProject.file(releaseStoreFilePath)
} else {
    null
}
val hasReleaseSigning = releaseKeystoreFile != null &&
    releaseKeystoreFile.isFile &&
    releaseStorePassword.isNotBlank() &&
    releaseKeyAlias.isNotBlank() &&
    releaseKeyPassword.isNotBlank()

android {
    namespace = "br.ufop.campus"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.nuncamorto.vidaacademica"
        minSdk = 26
        targetSdk = 35
        versionCode = 32
        versionName = "1.9.15"

        buildConfigField("String", "FEEDBACK_ENDPOINT", "\"$feedbackEndpoint\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseSigning && releaseKeystoreFile != null) {
            create("release") {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
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
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.window:window:1.3.0")

    implementation(platform("androidx.compose:compose-bom:2024.10.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("net.java.dev.jna:jna:5.14.0@aar")
    implementation("com.tom-roush:pdfbox-android:2.0.27.0")
    implementation("com.google.zxing:core:3.5.3")
    implementation("androidx.camera:camera-core:1.4.1")
    implementation("androidx.camera:camera-camera2:1.4.1")
    implementation("androidx.camera:camera-lifecycle:1.4.1")
    implementation("androidx.camera:camera-view:1.4.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20260814")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:2024.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
