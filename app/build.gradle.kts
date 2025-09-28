import java.util.Properties
import java.io.FileInputStream

// Version catalog for dependency management - 方案A稳态矩阵
object Versions {
    // Compose生态 (与Kotlin 1.9.24对齐)
    const val compose = "2024.09.00"
    const val composeCompiler = "1.5.14"  // 与Kotlin 1.9.24对齐
    
    // Core Android
    const val lifecycle = "2.7.0"
    const val activity = "1.9.2"  // 最新activity-compose
    const val core = "1.12.0"
    const val navigation = "2.8.2"  // 最新navigation-compose
    
    // 依赖注入 & 数据库 (KSP优化版本)
    const val hilt = "2.51.1"
    const val hiltExt = "1.1.0"
    const val room = "2.6.1"
    
    // 网络 & 异步
    const val retrofit = "2.9.0"
    const val okhttp = "4.12.0"
    const val coroutines = "1.7.3"
    const val workManager = "2.9.0"
    
    // Material (解决Theme.Material3依赖问题)
    const val material = "1.12.0"
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("dagger.hilt.android.plugin")
    id("androidx.room")
}

android {
    namespace = "com.psychat.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.psychat.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
        
        // Load API keys from secrets.properties
        val secretsFile = rootProject.file("secrets.properties")
        if (secretsFile.exists()) {
            val secrets = Properties()
            secrets.load(FileInputStream(secretsFile))
            buildConfigField("String", "ANTHROPIC_API_KEY", "\"${secrets.getProperty("ANTHROPIC_API_KEY", "")}\"")
        } else {
            buildConfigField("String", "ANTHROPIC_API_KEY", "\"\"")
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
    composeOptions {
        kotlinCompilerExtensionVersion = Versions.composeCompiler
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    room {
        schemaDirectory("$projectDir/schemas")
    }
}

// Configure KSP
kotlin {
    sourceSets {
        debug {
            kotlin.srcDir("build/generated/ksp/debug/kotlin")
        }
        release {
            kotlin.srcDir("build/generated/ksp/release/kotlin")
        }
    }
}

dependencies {
    // Core Android
    implementation("androidx.core:core-ktx:${Versions.core}")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:${Versions.lifecycle}")
    implementation("androidx.activity:activity-compose:${Versions.activity}")

    // Compose BOM - ensures all Compose libraries use compatible versions
    implementation(platform("androidx.compose:compose-bom:${Versions.compose}"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")

    // Navigation
    implementation("androidx.navigation:navigation-compose:${Versions.navigation}")

    // ViewModel & LiveData
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:${Versions.lifecycle}")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:${Versions.lifecycle}")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:${Versions.lifecycle}")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:${Versions.coroutines}")

    // Hilt Dependency Injection
    implementation("com.google.dagger:hilt-android:${Versions.hilt}")
    implementation("androidx.hilt:hilt-navigation-compose:${Versions.hiltExt}")
    implementation("androidx.hilt:hilt-work:${Versions.hiltExt}")
    ksp("com.google.dagger:hilt-compiler:${Versions.hilt}")
    ksp("androidx.hilt:hilt-compiler:${Versions.hiltExt}")

    // Room Database
    implementation("androidx.room:room-runtime:${Versions.room}")
    implementation("androidx.room:room-ktx:${Versions.room}")
    ksp("androidx.room:room-compiler:${Versions.room}")

    // Network - Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:${Versions.retrofit}")
    implementation("com.squareup.retrofit2:converter-gson:${Versions.retrofit}")
    implementation("com.squareup.okhttp3:okhttp:${Versions.okhttp}")
    implementation("com.squareup.okhttp3:logging-interceptor:${Versions.okhttp}")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx:${Versions.workManager}")

    // Material Design (解决Theme.Material3依赖问题)
    implementation("com.google.android.material:material:${Versions.material}")

    // Logging
    implementation("com.jakewharton.timber:timber:5.0.1")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:${Versions.coroutines}")
    testImplementation("androidx.room:room-testing:${Versions.room}")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    androidTestImplementation(platform("androidx.compose:compose-bom:${Versions.compose}"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Debug
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("com.squareup.leakcanary:leakcanary-android:2.12")
}
