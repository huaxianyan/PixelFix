plugins {
    id("com.android.application")
}

layout.buildDirectory = file("../.work/build/app")

// Signing material lives outside Git; see signing/signing-credentials.txt.
val credentialsFile = rootProject.file("signing/signing-credentials.txt")
val signingValues: Map<String, String> = if (credentialsFile.isFile) {
    credentialsFile.readLines()
        .filter { it.contains(": ") }
        .associate { it.substringBefore(": ") to it.substringAfter(": ") }
} else {
    emptyMap()
}
val signingKeystore = signingValues["keystore"]?.let { rootProject.file(it) }
val hasReleaseSigning = signingKeystore?.isFile == true &&
    !signingValues["storePassword"].isNullOrBlank() &&
    !signingValues["alias"].isNullOrBlank()

android {
    namespace = "com.neko7ina.pixelfix"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.neko7ina.pixelfix"
        minSdk = 37
        targetSdk = 37
        versionCode = 4
        versionName = "0.4.0"
        // Every fix targets this exact SystemUI APK. A fix that needs another build
        // must bring its own constants instead of widening this gate.
        buildConfigField("String", "SYSTEMUI_BUILD", "\"CP3A.261005.005\"")
        buildConfigField("String", "SYSTEMUI_SHA256",
            "\"d626069f146882d78af2133a7f91f3fad11a428ff7be288d3f1c5eb5effe50d6\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = signingKeystore
                storePassword = signingValues["storePassword"]
                keyAlias = signingValues["alias"]
                keyPassword = signingValues["keyPassword"] ?: signingValues["storePassword"]
                storeType = signingValues["storeType"] ?: "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
}

dependencies {
    compileOnly("io.github.libxposed:api:102.0.0")
    testImplementation("junit:junit:4.13.2")
}
