plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    useLibrary("android.test.runner")
    useLibrary("android.test.base")
    useLibrary("android.test.mock")
    namespace = "dev.luma.launcher"
    compileSdk = 35
    defaultConfig {
        applicationId = "dev.luma.launcher"
        minSdk = 23
        targetSdk = 35
        versionCode = 43
        versionName = "1.0.0-rc1"
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }
    buildTypes { release { isMinifyEnabled = false } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
    lint { abortOnError = true }
}
dependencies { testImplementation("junit:junit:4.13.2") }
