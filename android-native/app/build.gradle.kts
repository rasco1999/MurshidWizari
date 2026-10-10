plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
dependencies { testImplementation("junit:junit:4.13.2") }

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
    defaultConfig { applicationId = "com.murshidwizari.student"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
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
