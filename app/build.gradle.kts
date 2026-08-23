plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val oauthClientId = providers.gradleProperty("MUSABLAB_GITHUB_CLIENT_ID")
    .orElse("")
val controlRepo = providers.gradleProperty("MUSABLAB_CONTROL_REPO")
    .orElse("msabz/MUSABLAB")

android {
    namespace = "com.musablab.agent"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.musablab.agent"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        buildConfigField("String", "GITHUB_CLIENT_ID", "\"${oauthClientId.get()}\"")
        buildConfigField("String", "CONTROL_REPO", "\"${controlRepo.get()}\"")
        buildConfigField("String", "CONTROL_BRANCH", "\"main\"")
        buildConfigField("int", "AGENT_PROTOCOL", "1")
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += setOf("/META-INF/{AL2.0,LGPL2.1}")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    testImplementation("junit:junit:4.13.2")
}
