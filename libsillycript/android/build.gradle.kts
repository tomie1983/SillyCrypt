plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.dev.libsillycrypt"
    compileSdk = 36

    packaging {
        resources {
            excludes += "META-INF/versions/9/OSGI-INF/**"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters.addAll(arrayOf("arm64-v8a", "x86_64"))
        }
        externalNativeBuild {
            cmake {
                targets("cryptaes")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path("CMakeLists.txt")
        }
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
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.core.ktx)
    testImplementation(libs.junit)
    androidTestImplementation(libs.test.coroutines)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    api(project(":libsillycript:core"))
}