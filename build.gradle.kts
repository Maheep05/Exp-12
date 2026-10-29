plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.httpwebservicedemo"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.httpwebservicedemo"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
}
