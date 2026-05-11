plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hiltAndroid)
    alias(libs.plugins.serialization)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.sillycrypt.exfat_browser"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.android.activity)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.icons.extended)
    implementation(libs.androidx.material3)
    implementation(libs.hilt.navigation)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.immutable.collections)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    ksp(libs.androidx.hilt.compiler)
    implementation(libs.compose.navigation)
    implementation(libs.datastore)
    implementation(libs.immutable.collections)
    implementation(project(":exfat"))
}