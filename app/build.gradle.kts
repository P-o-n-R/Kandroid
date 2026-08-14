plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("com.google.devtools.ksp")
}

val releaseSigningValues = mapOf(
    "ANDROID_SIGNING_KEY_PATH" to providers.environmentVariable("ANDROID_SIGNING_KEY_PATH").orNull,
    "ANDROID_KEYSTORE_PASSWORD" to providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull,
    "ANDROID_KEY_ALIAS" to providers.environmentVariable("ANDROID_KEY_ALIAS").orNull,
    "ANDROID_KEY_PASSWORD" to providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull,
)
val hasReleaseSigningValue = releaseSigningValues.values.any { !it.isNullOrBlank() }
val hasCompleteReleaseSigning = releaseSigningValues.values.all { !it.isNullOrBlank() }

if (hasReleaseSigningValue && !hasCompleteReleaseSigning) {
    val missingVariables = releaseSigningValues
        .filterValues { it.isNullOrBlank() }
        .keys
        .sorted()
        .joinToString()
    throw GradleException("Incomplete release signing configuration. Missing: $missingVariables")
}

android {
    namespace = "com.kandroid.app"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.kandroid.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 2
        versionName = "1.0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.generatedDensities()
    }

    buildFeatures { compose = true }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    signingConfigs {
        if (hasCompleteReleaseSigning) {
            create("release") {
                storeFile = file(releaseSigningValues.getValue("ANDROID_SIGNING_KEY_PATH")!!)
                storePassword = releaseSigningValues.getValue("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = releaseSigningValues.getValue("ANDROID_KEY_ALIAS")
                keyPassword = releaseSigningValues.getValue("ANDROID_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        getByName("release") {
            if (hasCompleteReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging.resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.work:work-runtime:2.11.2")

    implementation("androidx.room:room-runtime:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")
    ksp("androidx.room:room-compiler:2.8.4")

    implementation("com.squareup.okhttp3:okhttp:5.4.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:5.4.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    testImplementation("androidx.glance:glance-testing:1.1.1")
    testImplementation("androidx.glance:glance-appwidget-testing:1.1.1")
    testImplementation("org.robolectric:robolectric:4.16.1")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }

tasks.register("validateReleaseMetadata") {
    group = "verification"
    description = "Validates a release tag against the app version and F-Droid changelog."

    val configuredVersionCode = android.defaultConfig.versionCode
        ?: throw GradleException("versionCode must be configured")
    val configuredVersionName = android.defaultConfig.versionName
        ?: throw GradleException("versionName must be configured")
    val releaseTag = providers.gradleProperty("releaseTag")
    val changelogFile = rootProject.file(
        "fastlane/metadata/android/en-US/changelogs/$configuredVersionCode.txt",
    )

    inputs.property("releaseTag", releaseTag)
    inputs.file(changelogFile)

    doLast {
        val tag = releaseTag.orNull
            ?: throw GradleException("Pass the release tag with -PreleaseTag=v$configuredVersionName")
        val expectedTag = "v$configuredVersionName"
        if (tag != expectedTag) {
            throw GradleException("Release tag '$tag' must exactly match '$expectedTag'")
        }
        if (configuredVersionCode <= 0) {
            throw GradleException("versionCode must be a positive integer")
        }

        val notes = changelogFile.readText(Charsets.UTF_8).trim()
        if (notes.isEmpty()) {
            throw GradleException("Release changelog must not be empty: ${changelogFile.path}")
        }
        val characterCount = notes.codePointCount(0, notes.length)
        if (characterCount > 500) {
            throw GradleException(
                "Release changelog must be at most 500 characters; found $characterCount: ${changelogFile.path}",
            )
        }

        logger.lifecycle(
            "Validated release $tag " +
                "(versionCode $configuredVersionCode, changelog $characterCount characters)",
        )
    }
}
