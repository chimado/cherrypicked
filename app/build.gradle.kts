plugins {
    id("com.android.application") // AGP 9 compiles Kotlin itself (built-in Kotlin)
}

android {
    namespace = "io.github.chimado.cherrypicked"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.chimado.cherrypicked"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    lint { checkReleaseBuilds = false }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
