plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
dependencies {
    implementation("com.google.firebase:firebase-messaging:25.0.2")
    testImplementation("junit:junit:4.13.2")
}

// Release keys must be supplied by the owner's secure build environment, never committed.
val productionStore = System.getenv("MURSHID_RELEASE_KEYSTORE")
val productionStorePassword = System.getenv("MURSHID_RELEASE_STORE_PASSWORD")
val productionAlias = System.getenv("MURSHID_RELEASE_KEY_ALIAS")
val productionKeyPassword = System.getenv("MURSHID_RELEASE_KEY_PASSWORD")
val productionSigningReady = listOf(productionStore, productionStorePassword, productionAlias,
    productionKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "com.murshidwizari.student"
    compileSdk = 35
    buildFeatures { buildConfig = true }
    defaultConfig { applicationId = "com.murshidwizari.student"; minSdk = 26; targetSdk = 35; versionCode = 49803; versionName = "4.8.19"; buildConfigField("String", "API_ORIGIN", "\"https://www.mur-iq.com/\"") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    signingConfigs {
        if (productionSigningReady) create("production") {
            storeFile = file(productionStore!!)
            storePassword = productionStorePassword!!
            keyAlias = productionAlias!!
            keyPassword = productionKeyPassword!!
        }
    }
    buildTypes {
        getByName("release") { signingConfig = signingConfigs.findByName("production") }
        create("qa") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".qa"
            versionNameSuffix = "-qa"
            buildConfigField("String", "API_ORIGIN", "\"http://127.0.0.1:8765/\"")
        }
        // Install alongside Build 498 even when the owner's original signing key differs.
        // This private preview uses the real API; QA uses the local synthetic fixture.
        create("preview") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".preview"
            versionNameSuffix = "-preview"
            buildConfigField("String", "API_ORIGIN", "\"https://www.mur-iq.com/\"")
        }
        create("candidate") {
            initWith(getByName("release"))
            signingConfig = null
            isDebuggable = false
        }
    }
}

tasks.register("verifyReleaseSigning") {
    doLast {
        check(productionSigningReady && file(productionStore ?: "").isFile) {
            "Production signing is missing. Configure MURSHID_RELEASE_KEYSTORE and the three signing credentials securely before generating a release."
        }
    }
}
tasks.configureEach {
    if (name == "assembleRelease" || name == "bundleRelease") dependsOn("verifyReleaseSigning")
}
