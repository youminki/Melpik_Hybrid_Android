plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android) // jetbrains → kotlin
}

android {
    namespace = "com.youminki.testhybrid"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.youminki.testhybrid"
        minSdk = 24
        targetSdk = 35
        versionCode = 9
        versionName = "9.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            storeFile = file("../new_upload_key.jks")
            storePassword = "zz0722zz0722!"      // keystore 비밀번호
            keyAlias = "upload"                  // 반드시 upload로!
            keyPassword = "zz0722zz0722!"        // upload alias의 비밀번호 (keystore와 같으면 그대로)
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}