plugins { id("com.android.application") version "8.7.3" }
android {
    namespace = "dev.luma.remote"
    compileSdk = 35
    defaultConfig { applicationId = "dev.luma.remote"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0" }
    val signingNames=listOf("LUMA_RELEASE_STORE_FILE","LUMA_RELEASE_STORE_PASSWORD","LUMA_RELEASE_KEY_ALIAS","LUMA_RELEASE_KEY_PASSWORD")
    if(signingNames.all{!System.getenv(it).isNullOrBlank()}) {
        signingConfigs { create("remoteRelease") { storeFile=file(System.getenv(signingNames[0]));storePassword=System.getenv(signingNames[1]);keyAlias=System.getenv(signingNames[2]);keyPassword=System.getenv(signingNames[3]) } }
        buildTypes { getByName("release") { signingConfig=signingConfigs.getByName("remoteRelease") } }
    }
    compileOptions { sourceCompatibility=JavaVersion.VERSION_17; targetCompatibility=JavaVersion.VERSION_17 }
    lint { abortOnError=true }
}
dependencies { testImplementation("junit:junit:4.13.2") }
