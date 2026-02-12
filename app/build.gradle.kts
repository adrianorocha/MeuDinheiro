plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.3.2"
}

android {
    namespace = "com.meudinheiro"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "com.meudinheiro"
        minSdk = 25
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:2.10.0")
    implementation(libs.androidx.compose.runtime.livedata)
    implementation(libs.androidx.compose.foundation)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation(libs.androidx.material3)
    implementation(libs.compose.material3)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.compose.foundation.layout)
    implementation(libs.androidx.compose.remote.creation.core)
    implementation("androidx.datastore:datastore-preferences:1.0.0")
    implementation(libs.androidx.fragment.ktx)
    implementation("androidx.biometric:biometric:1.1.0")
    implementation(libs.androidx.foundation)
    implementation("io.coil-kt:coil-compose:2.2.2")
    implementation(libs.androidx.foundation.layout)
    implementation("androidx.compose.material:material-icons-extended:1.4.3")
    implementation("androidx.biometric:biometric:1.2.0-alpha04")
    implementation(libs.androidx.appcompat)
    implementation("androidx.compose.material3:material3:1.0.0")
    implementation(libs.androidx.compose.animation.core)
    implementation(libs.androidx.compose.ui.unit)
    implementation(libs.androidx.work.runtime.ktx)
    implementation("androidx.work:work-runtime-ktx:2.7.1")
    implementation(libs.androidx.ui)
    implementation("com.google.code.gson:gson:2.10.1")
    implementation(libs.androidx.runtime)
    implementation("androidx.biometric:biometric:1.2.0-alpha05")
    implementation(libs.androidx.compose.ui.geometry)
    implementation("androidx.glance:glance-appwidget:1.0.0")
    implementation("androidx.glance:glance-material3:1.0.0")

    implementation("androidx.glance:glance:1.1.0")
    implementation("androidx.glance:glance-appwidget:1.1.0")
    implementation("androidx.glance:glance-material3:1.1.0")
    implementation(libs.navigation.compose)
    implementation(libs.androidx.ui.text)

    implementation("com.google.zxing:core:3.5.3")
    implementation("com.airbnb.android:lottie-compose:6.4.0")
    implementation(libs.androidx.compose.animation)

    ksp("androidx.room:room-compiler:2.8.4")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}