
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
}

// Local development reads keystore.properties.
// CI uses GitHub Actions environment variables.
// Environment variables take precedence.
val signingProps = Properties()
val signingFile = rootProject.file("keystore.properties")

if (signingFile.exists()) {
    signingFile.inputStream().use { signingProps.load(it) }
}

fun signingValue(envName: String, propName: String): String? =
    System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: signingProps.getProperty(propName)?.takeIf { it.isNotBlank() }

android {
    namespace = "com.example.helper"

    compileSdk {
        version = release(37)
    }

    signingConfigs {
        create("release") {
            val keystorePath = signingValue("ANDROID_KEYSTORE_PATH", "storeFile")
            val keystorePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
            val keyAliasValue = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
            val keyPasswordValue = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")

            if (
                keystorePath != null &&
                keystorePassword != null &&
                keyAliasValue != null &&
                keyPasswordValue != null
            ) {
                storeFile = file(keystorePath)
                storePassword = keystorePassword
                keyAlias = keyAliasValue
                keyPassword = keyPasswordValue
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.helper"
        minSdk = 24
        targetSdk = 37
        versionCode = 2
        versionName = "1.1.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            val keystorePath = signingValue("ANDROID_KEYSTORE_PATH", "storeFile")
            val keystorePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
            val keyAliasValue = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
            val keyPasswordValue = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")

            val signingValuesPresent =
                keystorePath != null &&
                    keystorePassword != null &&
                    keyAliasValue != null &&
                    keyPasswordValue != null

            if (signingValuesPresent) {
                val keystoreFile = file(keystorePath!!)
                if (keystoreFile.isFile) {
                    signingConfig = signingConfigs.getByName("release")
                }
            }
        }
        release {
            val keystorePath = signingValue("ANDROID_KEYSTORE_PATH", "storeFile")
            val keystorePassword = signingValue("ANDROID_KEYSTORE_PASSWORD", "storePassword")
            val keyAliasValue = signingValue("ANDROID_KEY_ALIAS", "keyAlias")
            val keyPasswordValue = signingValue("ANDROID_KEY_PASSWORD", "keyPassword")

            val signingValuesPresent =
                keystorePath != null &&
                    keystorePassword != null &&
                    keyAliasValue != null &&
                    keyPasswordValue != null

            if (signingValuesPresent) {
                val keystoreFile = file(keystorePath!!)

                if (!keystoreFile.isFile) {
                    throw GradleException(
                        "Release signing keystore does not exist: $keystoreFile"
                    )
                }

                signingConfig = signingConfigs.getByName("release")
            } else if (
                gradle.startParameter.taskNames.any {
                    it.contains("Release", ignoreCase = true) &&
                        !it.contains("UnitTest", ignoreCase = true) &&
                        !it.contains("Lint", ignoreCase = true)
                }
            ) {
                throw GradleException(
                    "Release signing requires either the four ANDROID_KEYSTORE_* " +
                        "environment variables (ANDROID_KEY_ALIAS and ANDROID_KEY_PASSWORD " +
                        "included), or all four values in keystore.properties."
                )
            }

            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11

        // Enable java.time support on older Android versions.
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)

    // Material icons.
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.room.runtime)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    // CSV/XLSX import and local photo loading.
    implementation(libs.commons.csv)
    implementation(libs.kexcel.android)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
