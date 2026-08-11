plugins {
    id("rikkahub.android.library")
}

// Fail fast when the gitignored MNN prerequisites are missing (fresh clone / CI):
// native sources + headers come from vendor/MNN, the prebuilt runtime from
// mnn-prebuilt/arm64-v8a/libMNN.so. Without them the CMake build and packaging
// would fail deep inside the native toolchain with cryptic errors.
val mnnSourceMarker = rootProject.file("vendor/MNN/CMakeLists.txt")
val mnnPrebuiltSo = rootProject.file("mnn-prebuilt/arm64-v8a/libMNN.so")
if (!mnnSourceMarker.exists() || !mnnPrebuiltSo.exists()) {
    throw GradleException(
        buildString {
            appendLine(":mnn is missing gitignored MNN prerequisites:")
            if (!mnnSourceMarker.exists()) appendLine("  - vendor/MNN (MNN source tree, commit 1d535d7)")
            if (!mnnPrebuiltSo.exists()) appendLine("  - mnn-prebuilt/arm64-v8a/libMNN.so (prebuilt runtime)")
            appendLine("Run the setup script first to fetch/build them:")
            appendLine("  Windows:  powershell -File scripts/setup-mnn.ps1")
            appendLine("  Linux:  ./scripts/setup-mnn.sh")
        }
    )
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
