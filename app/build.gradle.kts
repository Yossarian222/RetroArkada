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
    compileSdk = 37

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

    // Release kľúč je len v GitHub Secrets (ARKADA_KEYSTORE_B64, ARKADA_KS_PASS), nikdy v repozitári.
    // CI ho rozbalí do súboru a cestu dá do ARKADA_KS_PATH. Každá verzia podpísaná týmto kľúčom
    // sa nainštaluje ako aktualizácia (pozície ostanú). Lokálny build bez kľúča použije debug kľúč.
    val ksPath = System.getenv("ARKADA_KS_PATH")?.takeIf { it.isNotBlank() }
    signingConfigs {
        if (ksPath != null) {
            create("arkada") {
                storeFile = file(ksPath)
                storePassword = System.getenv("ARKADA_KS_PASS")
                keyAlias = "retroarkada"
                keyPassword = System.getenv("ARKADA_KS_PASS")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (ksPath != null) signingConfigs.getByName("arkada") else signingConfigs.getByName("debug")
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
