import java.util.Properties

plugins {
    id("com.android.application") // AGP 9 compiles Kotlin itself (built-in Kotlin)
}

// Release signing comes from outside the repo. Without it the release build is signed with the local debug key.
val keystoreProps = File(System.getProperty("user.home"), ".config/cherrypicked-release/keystore.properties")

android {
    namespace = "io.github.chimado.cherrypicked"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.chimado.cherrypicked"
        minSdk = 31
        targetSdk = 36
        // The commit count: a build from a later commit always installs over an earlier one. 1 outside a git checkout.
        val commits = runCatching {
            ProcessBuilder("git", "rev-list", "--count", "HEAD").directory(rootDir).start().inputStream.bufferedReader().readText().trim().toInt()
        }.getOrDefault(1)
        versionCode = commits
        // -Pnightly marks a test build; its name matches the pre-release tag scripts/nightly-release.sh publishes.
        versionName = providers.gradleProperty("cherrypicked.version").get() + if (providers.gradleProperty("nightly").isPresent) "-nightly.$commits" else ""
    }
    signingConfigs {
        if (keystoreProps.isFile) create("release") {
            val p = Properties().apply { keystoreProps.inputStream().use { load(it) } }
            fun prop(k: String) = p.getProperty(k) ?: throw GradleException("$k missing from ${keystoreProps.path}")
            storeFile = File(prop("storeFile")).let { if (it.isAbsolute) it else File(keystoreProps.parentFile, it.path) }
            storePassword = prop("storePassword")
            keyAlias = prop("keyAlias")
            keyPassword = prop("keyPassword")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.getByName(if (keystoreProps.isFile) "release" else "debug")
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
