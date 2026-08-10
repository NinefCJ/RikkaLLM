plugins {
    id("rikkahub.android.library")
}

android {
    namespace = "me.rerere.rikkallm.mnn"

    ndkVersion = "25.1.8937393"

    defaultConfig {
        ndk {
            // MNN prebuilt runtime is only provided for arm64-v8a
            abiFilters += listOf("arm64-v8a")
        }
        externalNativeBuild {
            cmake {
                cppFlags += "-std=c++17"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    sourceSets {
        getByName("main") {
            // Package the prebuilt libMNN.so (kept out of git via .gitignore) into the APK
            jniLibs.srcDirs("src/main/jniLibs", "../mnn-prebuilt")
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // Gson (ModelConfig / FileSplitter)
    implementation(libs.gson)

    // kotlinx
    implementation(libs.kotlinx.coroutines.core)

    // ktor server (local OpenAI-compatible API, Phase 2)
    api(libs.ktor.server.core)
    implementation(libs.ktor.server.cio)
    implementation(libs.ktor.server.host.common)

    // koin (LocalMnnManager registration / service injection)
    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)

    // tests
    testImplementation(libs.junit)
}
