plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.nmsevinf.studyfit"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nmsevinf.studyfit"
        minSdk = 28
        targetSdk = 35
        versionCode = 6
        versionName = "1.4"
    }

    // Clave fija del proyecto: así cada APK nueva se instala ENCIMA de la anterior
    // sin perder tus datos (sesiones, exámenes, ajustes).
    signingConfigs {
        create("studyfit") {
            storeFile = file("studyfit.keystore")
            storePassword = "studyfit"
            keyAlias = "studyfit"
            keyPassword = "studyfit"
        }
    }

    buildTypes {
        debug { signingConfig = signingConfigs.getByName("studyfit") }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("studyfit")
        }
    }
    lint { abortOnError = false }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
