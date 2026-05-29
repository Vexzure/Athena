plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.ksp)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.hilt)
  alias(libs.plugins.kotlin.parcelize)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.spotless)
}

android {
  namespace = "com.kin.athena"
  compileSdk = 37
  buildToolsVersion = "36.1.0"

  defaultConfig {
    applicationId = "com.kin.athena"
    minSdk = 24
    targetSdk = 37
    versionCode = 306
    versionName = "1.80"

    vectorDrawables { useSupportLibrary = true }
    androidResources { localeFilters += listOf("en", "ca", "de", "es", "fr", "pl") }
    externalNativeBuild {
      cmake {
        cppFlags += ""
        abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
      }
    }
    ndk { abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64") }
  }

  // Enable ABI splits for smaller APKs
  splits {
    abi {
      isEnable = true
      reset()
      include("arm64-v8a", "armeabi-v7a", "x86", "x86_64")
      isUniversalApk = true
    }
  }

  // Bundle optimization
  bundle {
    language { enableSplit = true }
    density { enableSplit = true }
    abi { enableSplit = true }
  }

  flavorDimensions += "store"
  productFlavors {
    create("playstore") {
      dimension = "store"
      buildConfigField("boolean", "USE_PLAY_BILLING", "true")
      buildConfigField("boolean", "CHECK_PREMIUM_CODE", "true")
      buildConfigField("String", "KOFI_URL", "\"https://ko-fi.com/s/b127ca6671\"")
    }
    create("fdroid") {
      dimension = "store"
      buildConfigField("boolean", "USE_PLAY_BILLING", "false")
      buildConfigField("boolean", "CHECK_PREMIUM_CODE", "true")
      buildConfigField(
        "String",
        "KOFI_URL",
        "\"https://buy.stripe.com/test_00weVe0vCgkd09x8RY3ZK01\"",
      )
    }
  }
  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
      signingConfig = signingConfigs.getByName("debug")

      // Additional APK optimization
      ndk { debugSymbolLevel = "SYMBOL_TABLE" }
    }
    debug {
      isMinifyEnabled = false
      isShrinkResources = false
      proguardFiles(
        getDefaultProguardFile("proguard-android-optimize.txt"),
        "proguard-rules.pro",
      )
      signingConfig = signingConfigs.getByName("debug")
      isDebuggable = true

      // Speed up debug builds
      ndk { debugSymbolLevel = "NONE" }
    }
  }

  tasks.whenTaskAdded {
    if (name.startsWith("merge") && name.contains("JniLibFolders")) {
      dependsOn("buildGoLibraries")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlin {
    compilerOptions {
      jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
      freeCompilerArgs.addAll("-opt-in=kotlin.RequiresOptIn", "-Xjvm-default=all")
    }
    jvmToolchain(17)
  }
  buildFeatures {
    compose = true
    buildConfig = true
    aidl = true
    // Disable unused features for faster builds
    renderScript = false
    resValues = false
    shaders = false
  }

  // Compose compiler optimizations
  composeCompiler {
    enableStrongSkippingMode = true
    enableNonSkippingGroupOptimization = true
    enableIntrinsicRemember = true
  }
  packaging {
    resources {
      excludes +=
        setOf(
          "/META-INF/{AL2.0,LGPL2.1}",
          "/META-INF/LICENSE.md",
          "/META-INF/README.md",
          "/META-INF/DEPENDENCIES",
          "/META-INF/INDEX.LIST",
        )
    }
  }
  sourceSets { getByName("main") { jniLibs.srcDir("libs/") } }

  tasks.register<Exec>("buildGoLibraries") {
    group = "build"
    description = "Build Go libraries for Android"

    val goDir = layout.projectDirectory.dir("src/main/go")
    val libsDir = layout.projectDirectory.dir("libs")

    inputs.dir(goDir)
    outputs.files(
      libsDir.file("arm64-v8a/libnflog.so"),
      libsDir.file("armeabi-v7a/libnflog.so"),
    )
    outputs.upToDateWhen { false }

    workingDir(goDir)

    val isWindows = org.gradle.internal.os.OperatingSystem.current().isWindows
    val buildScript = if (isWindows) "build-android.bat" else "build-android.sh"

    if (isWindows) {
      commandLine("cmd", "/c", buildScript)
    } else {
      commandLine("bash", buildScript)
    }

    doFirst {
      val buildScriptFile = goDir.file(buildScript).asFile
      if (!buildScriptFile.exists()) {
        throw GradleException("Build script $buildScript not found in ${goDir.asFile}")
      }

      libsDir.dir("arm64-v8a").asFile.mkdirs()
      libsDir.dir("armeabi-v7a").asFile.mkdirs()
    }
  }
  ksp {
    arg("dagger.fastInit", "enabled")
    arg("dagger.formatGeneratedSource", "disabled")
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
    arg("room.expandProjection", "true")
  }
  externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }

  // Override cmake path to use system cmake (ARM64)
  ndkVersion = "29.0.14206865"
  packaging { jniLibs.useLegacyPackaging = true }

  // Lint optimizations - skip during builds
  lint {
    checkReleaseBuilds = false
    abortOnError = false
    checkDependencies = false
  }

  // Enable baseline profiles for better runtime performance
  experimentalProperties["android.experimental.enableArtProfiles"] = true
}

configurations.all {
  resolutionStrategy {
    force("androidx.lifecycle:lifecycle-runtime:2.9.0")
    force("androidx.lifecycle:lifecycle-runtime-ktx:2.9.0")
    force("androidx.lifecycle:lifecycle-viewmodel:2.9.0")
    force("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.0")
    force("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    force("androidx.lifecycle:lifecycle-viewmodel-savedstate:2.9.0")
    force("androidx.lifecycle:lifecycle-common:2.9.0")
    force("androidx.lifecycle:lifecycle-livedata-core:2.9.0")
  }
}

dependencies {
  implementation(libs.m3color)

  // Hilt
  implementation(libs.hilt.android)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.androidx.hilt.common)
  ksp(libs.hilt.android.compiler)
  ksp(libs.hilt.compile)
  implementation(libs.hilt.navigation.compose)
  implementation(libs.androidx.hilt.work)

  // Room
  implementation(libs.androidx.room.runtime)
  ksp(libs.androidx.room.compiler)
  implementation(libs.androidx.room.ktx)

  // Compose
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.core.splashscreen)

  // AndroidX
  implementation(libs.androidx.appcompat)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.datastore.preferences)

  // Lifecycle - explicit versions to avoid conflicts
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Retrofit
  implementation(libs.retrofit)
  implementation(libs.converter.gson)
  implementation(libs.converter.scalars)

  // Notification
  implementation(libs.androidx.core)

  // Fingerprint
  implementation(libs.androidx.biometric)

  // DNS Blocking
  implementation(libs.atomicfu)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.dnsjava)
  implementation(libs.pcap4j.core)

  // Billing (Play Store only)
  "playstoreImplementation"(libs.billing)

  // Shizuku
  implementation(libs.shizuku.api)
  implementation(libs.shizuku.provider)
}

spotless {
  kotlin {
    target("**/*.kt")
    targetExclude("**/build/**/*.kt")
    // ktlint with strict rules - fix violations manually
    ktlint("1.2.1")
      .editorConfigOverride(
        mapOf(
          "ktlint_code_style" to "ktlint_official",
          "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
          "max_line_length" to "120",
        ),
      )
  }
  kotlinGradle {
    target("*.gradle.kts")
    ktlint("1.2.1")
  }
}
