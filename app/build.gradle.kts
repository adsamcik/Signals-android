import org.jetbrains.dokka.gradle.DokkaTask
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.net.URL

plugins {
	id("com.android.application")
	id("org.jetbrains.dokka-android")
	id("com.google.gms.oss.licenses.plugin")
	id("io.fabric")
	Libraries.corePlugins(this)
}


if (file("key.gradle").exists()) {
	apply("key.gradle")
}

android {
	compileSdkVersion(Android.compile)
	buildToolsVersion(Android.buildTools)
	defaultConfig {
		applicationId = "com.adsamcik.signalcollector"
		minSdkVersion(Android.min)
		targetSdkVersion(Android.target)
		versionCode = 310
		versionName = "7.0α27 Offline"
		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
	}

	with(compileOptions) {
		sourceCompatibility = JavaVersion.VERSION_1_8
		targetCompatibility = JavaVersion.VERSION_1_8
	}

	tasks.withType<KotlinCompile> {
		with(kotlinOptions) {
			jvmTarget = "1.8"
			freeCompilerArgs = listOf("-Xuse-experimental=kotlin.ExperimentalUnsignedTypes")
		}
	}

	buildTypes {
		getByName("debug") {
			isTestCoverageEnabled = true
		}

		create("release_nominify") {
			isMinifyEnabled = false
		}
		getByName("release") {
			isMinifyEnabled = true
			proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
		}
	}

	lintOptions.isCheckReleaseBuilds = false
	sourceSets.getByName("androidTest").assets.srcDir("$projectDir/schemas")

	packagingOptions {
		pickFirst("META-INF/atomicfu.kotlin_module")
	}

	dynamicFeatures = mutableSetOf(":statistics", ":game", ":map")


}

tasks.withType<DokkaTask> {
	outputFormat = "html"
	outputDirectory = "$buildDir/javadoc"
	jdkVersion = 8
	skipEmptyPackages = true
	skipDeprecated = true

	externalDocumentationLink {
		url = URL("https://developer.android.com/reference/")
		packageListUrl = URL("https://developer.android.com/reference/android/support/package-list")
	}
}

dependencies {
	implementation(project(":common"))
	implementation(project(":tracker"))
	implementation(project(":activity"))

	Libraries.core(this)
	//1st party dependencies
	implementation("com.adsamcik.android-components:slider:0.8.0")
	Libraries.draggable(this)

	Libraries.introduction(this)

	//3rd party dependencies
	Libraries.moshi(this)

	implementation("com.jaredrummler:colorpicker:1.1.0")

	implementation("de.psdev.licensesdialog:licensesdialog:2.1.0")
	Libraries.fileChooser(this)

	//GPX
	implementation("stax:stax-api:1.0.1")
	implementation("com.fasterxml:aalto-xml:1.2.1")
	implementation("io.jenetics:jpx:1.5.2")

	//Google dependencies
	implementation("com.google.android:flexbox:1.1.0")

	//AndroidX
	implementation("androidx.cardview:cardview:1.0.0")
	Libraries.preference(this)

	//PlayServices
	Libraries.location(this)
	Libraries.crashlytics(this)

	//Room
	Libraries.database(this)


	Libraries.test(this)
	//workaround  Multiple APKs packaging the same library can cause runtime errors.
	implementation(project(":commonmap"))
	Libraries.map(this)
}
apply {
	plugin("com.google.gms.google-services")
}
