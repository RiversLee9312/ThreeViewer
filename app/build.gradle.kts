import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.threeviewer"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.threeviewer"
        minSdk = 31
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    val signingFile = file(System.getenv("THREEVIEWER_SIGNING_PROPERTIES")
        ?: "${System.getProperty("user.home")}/.android/threeviewer-signing.properties")
    if (signingFile.isFile) {
        val signing = Properties().apply { signingFile.inputStream().use(::load) }
        signingConfigs.create("release") {
            storeFile = file(signing.getProperty("storeFile"))
            storePassword = signing.getProperty("storePassword")
            keyAlias = signing.getProperty("keyAlias")
            keyPassword = signing.getProperty("keyPassword")
        }
    }
    buildTypes {
        debug { isPseudoLocalesEnabled = true }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    testOptions { unitTests.isReturnDefaultValues = true }
}

kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.10.01"))
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3:1.5.0-alpha07")
    implementation("com.google.android.filament:filament-android:1.77.3")
    implementation("com.google.android.filament:gltfio-android:1.77.3")
    implementation("com.google.android.filament:filament-utils-android:1.77.3")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20250517")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.10.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4-accessibility")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
    // Keep AGP's app/test runtime consistency while accommodating ATF's test-only Guava.
    debugImplementation("androidx.concurrent:concurrent-futures:1.2.0")
    debugImplementation("com.google.guava:guava:31.0.1-android")
    constraints {
        debugImplementation("org.checkerframework:checker-qual:3.22.1")
        debugImplementation("com.google.errorprone:error_prone_annotations:2.30.0")
    }
}

tasks.register("writeRuntimeDependencies") {
    description = "Export the resolved release components for the bundled license inventory."
    doLast {
        val components = configurations.getByName("releaseRuntimeClasspath").incoming.resolutionResult
            .allComponents.mapNotNull { it.moduleVersion?.toString() }.distinct().sorted()
        val output = layout.buildDirectory.file("reports/runtime-dependencies.txt").get().asFile
        output.parentFile.mkdirs()
        output.writeText(components.joinToString("\n"))
    }
}
