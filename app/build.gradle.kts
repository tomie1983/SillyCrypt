plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hiltAndroid)
    alias(libs.plugins.serialization)
}

android {
    namespace = "com.dev.sillycrypt"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.dev.sillycrypt"
        minSdk = 28
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
    buildFeatures {
        compose = true
    }
    packaging {
        resources.excludes.add("META-INF/versions/9/OSGI-INF/MANIFEST.MF")
    }
}


dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.icons.extended)
    implementation(libs.compose.navigation)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.fragment.ktx)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.datastore)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.immutable.collections)
    implementation(libs.hilt.navigation)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    implementation(project(":libsillycript:android"))
    implementation(project(":exfat-android"))
    implementation(project(":exfat-browser"))
    implementation(project(":core:serialization"))
    implementation(project(":workprofile"))
    implementation(project(":settings"))
}