import com.android.build.api.dsl.ApplicationExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

val buildNumber = providers.environmentVariable("GITHUB_RUN_NUMBER").orElse("1").get().toInt()

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_17
    }
}

configure<ApplicationExtension> {
    namespace = "com.yossarian222.retroarkada"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.yossarian222.retroarkada"
        minSdk = 26
        targetSdk = 36
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
        ndk {
            // Raspberry Pi 5 (Android TV) je 64-bit ARM
            abiFilters += "arm64-v8a"
        }
    }

    signingConfigs {
        // Pevný kľúč v repozitári, aby sa každá nová verzia nainštalovala ako aktualizácia
        // (inak by sa pri preinštalovaní stratili uložené pozície).
        create("arkada") {
            storeFile = file("retroarkada.keystore")
            storePassword = "retroarkada"
            keyAlias = "retroarkada"
            keyPassword = "retroarkada"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("arkada")
        }
        debug {
            signingConfig = signingConfigs.getByName("arkada")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // Jadrá sa musia rozbaliť na disk, aby ich LibretroDroid vedel načítať cez dlopen
            useLegacyPackaging = true
        }
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.2")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.2")
    implementation("com.github.Swordfish90:LibretroDroid:0.14.0")
    implementation("org.nanohttpd:nanohttpd:2.3.1")
    implementation("com.google.zxing:core:3.5.3")
}
