plugins {
    id("com.android.application")
    kotlin("android")
    kotlin("android.extensions")
}

android {
    compileSdkVersion(27)
    defaultConfig {
        applicationId = "com.example.myapplication"
        minSdkVersion(27)
        targetSdkVersion(27)
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }
    }

    flavorDimensions("color")

    productFlavors {
        create("red") {
            setDimension("color")
            testInstrumentationRunner = "android.support.test.runner.AndroidJUnitRunner"
        }
    }

    testOptions {
        execution = "android_test_orchestrator"
    }
}

val testVariable = "com.android.support:exifinterface:27.0.2"

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlin_version")
    implementation("com.android.support.constraint:constraint-layout:1.0.2")
    implementation("com.android.support:recyclerview-v7:$rv_version")
    testImplementation("junit:junit:4.12")
    androidTestImplementation("com.android.support.test:runner:1.0.1")
    androidTestImplementation("com.android.support.test.espresso:espresso-core:3.0.1")
    implementation(mapOf("group" to "com.android.support", "name" to "appcompat-v7", "version" to "27.0.2"))
    implementation(testVariable)
    implementation("androidx.core:core-ktx:0.3")
    implementation("androidx.core:newer-core-ktx:2.0.0-alpha1")
    implementation("androidx.core:newer-version-ktx:1.2.0")
    implementation("androidx.core:variable-ktx:$variable")
}
