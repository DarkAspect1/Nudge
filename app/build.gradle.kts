import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.nudge.edge"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nudge.edge"
        minSdk = 26
        targetSdk = 35
        versionCode = 5
        versionName = "2.5.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val fileName = keystoreProperties.getProperty("storeFile")
            if (!fileName.isNullOrBlank()) {
                storeFile = file(fileName)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
        aidl = true
    }

    flavorDimensions += "distribution"
    productFlavors {
        create("online") {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_UPDATER", "true")
            buildConfigField("String", "GITHUB_OWNER", "\"DarkAspect1\"")
            buildConfigField("String", "GITHUB_REPO", "\"Nudge\"")
        }
        create("offline") {
            dimension = "distribution"
            buildConfigField("boolean", "ENABLE_UPDATER", "false")
            buildConfigField("String", "GITHUB_OWNER", "\"\"")
            buildConfigField("String", "GITHUB_REPO", "\"\"")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val flavor = variant.flavorName?.lowercase() ?: if (variant.name.contains("online", ignoreCase = true)) "online" else "offline"
        val apkName = "Nudge-$flavor.apk"
        variant.outputs.forEach { output ->
            output.outputFileName.set(apkName)
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.savedstate.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.datastore.preferences)
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:4.3")
    debugImplementation(libs.androidx.compose.ui.tooling)
    testImplementation(libs.junit)
    testImplementation("org.json:json:20240303")
}
