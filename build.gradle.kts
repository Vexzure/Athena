plugins {
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.kotlin.parcelize) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.detekt) apply false
}

// Dependency resolution optimization
allprojects {
    configurations.all {
        resolutionStrategy {
            // Cache dynamic versions for 24 hours
            cacheDynamicVersionsFor(24, "hours")
            // Cache changing modules for 24 hours
            cacheChangingModulesFor(24, "hours")
            // Force newer versions for common conflicts
            force("androidx.lifecycle:lifecycle-viewmodel:2.8.7")
            force("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
            force("androidx.lifecycle:lifecycle-runtime:2.8.7")
            force("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
            force("androidx.lifecycle:lifecycle-livedata-core:2.8.7")
        }
    }
}