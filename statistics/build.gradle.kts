val kotlin_version: String by extra
apply plugin: "com.android.dynamic-feature"
apply plugin: "kotlin-android-extensions"
apply plugin: "kotlin-android"

android {
    compileSdkVersion $compile_sdk


    defaultConfig {
        minSdkVersion $min_sdk
        targetSdkVersion $compile_sdk
        versionCode 1
        versionName "1.0"


    }


}

dependencies {
    implementation "com.github.PhilJay:MPAndroidChart:3.1.0"

    implementation(fileTree(dir: "libs", include: ["*.jar"]))
    implementation project(":app")
    implementation "androidx.core:core-ktx:1.1.0-alpha05"
    implementation "org.jetbrains.kotlin:kotlin-stdlib-jdk7:$kotlin_version"
    compile("androidx.core:core-ktx:+")
    implementation(kotlinModule("stdlib-jdk7", kotlin_version))
}
repositories {
    mavenCentral()
}
apply {
    plugin("kotlin-android")
}
apply {
    plugin("kotlin-android-extensions")
}