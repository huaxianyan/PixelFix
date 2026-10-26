plugins {
    id("com.android.application")
}

layout.buildDirectory = file("../.work/build/app")

android {
    namespace = "com.neko7ina.pixelfix"
    compileSdk = 37
    compileSdkMinor = 2

    defaultConfig {
        applicationId = "com.neko7ina.pixelfix"
        minSdk = 37
        targetSdk = 37
        versionCode = 3
        versionName = "0.3.0"
        // Gate per fix, never per module: a future fix brings its own verified build.
        buildConfigField("String", "DREAM_LOCKS_BUILD", "\"CP3A.261005.005\"")
        buildConfigField("String", "DREAM_LOCKS_SYSTEMUI_SHA256",
            "\"d626069f146882d78af2133a7f91f3fad11a428ff7be288d3f1c5eb5effe50d6\"")
    }

    buildFeatures {
        buildConfig = true
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
