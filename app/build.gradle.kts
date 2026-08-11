plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.delee.srdemo"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.delee.srdemo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
    }

    // Keep the model uncompressed so LiteRT can memory-map the asset.
    androidResources {
        noCompress += "tflite"
    }
}

val verifyBundledSrModel by tasks.registering {
    group = "verification"
    description = "Checks that the bundled SR model is a TFLite FlatBuffer."

    val modelFile = layout.projectDirectory.file("src/main/assets/sr_x4.tflite")
    inputs.file(modelFile)

    doLast {
        val file = modelFile.asFile
        check(file.isFile) { "Missing model asset: ${file.absolutePath}" }
        val bytes = file.readBytes()
        check(bytes.size >= 32) { "Model asset is unexpectedly small: ${bytes.size} bytes" }
        check(bytes.copyOfRange(4, 8).decodeToString() == "TFL3") {
            "Model asset is not a valid TFLite FlatBuffer (missing TFL3 identifier)."
        }
        logger.lifecycle("Verified ${file.name}: ${bytes.size} bytes, TFL3 identifier present")
    }
}

tasks.named("preBuild").configure {
    dependsOn(verifyBundledSrModel)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Current LiteRT CompiledModel Kotlin API.
    implementation("com.google.ai.edge.litert:litert:2.1.0")

    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
