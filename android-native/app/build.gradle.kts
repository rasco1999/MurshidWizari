plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
dependencies { testImplementation("junit:junit:4.13.2") }
android {
    namespace = "com.murshidwizari.student"
    compileSdk = 35
    defaultConfig { applicationId = "com.murshidwizari.student"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
