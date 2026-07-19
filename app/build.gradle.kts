plugins {
    alias(libs.plugins.android.application)
    id("com.google.devtools.ksp")
    id("androidx.navigation.safeargs.kotlin")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.pando.app"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.jollibee.frontend"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    implementation("androidx.activity:activity-ktx:1.13.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
    implementation("com.github.bumptech.glide:glide:5.0.9")
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("id.zelory:compressor:3.0.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.intuit.sdp:sdp-android:1.1.1")
    implementation("io.github.chaosleung:pinview:1.4.4")
    implementation("androidx.security:security-crypto:1.1.0")
    implementation("com.google.android.gms:play-services-location:21.4.0")
    implementation("androidx.cardview:cardview:1.0.0")
    implementation("io.coil-kt.coil3:coil:3.5.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.3.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("com.auth0.android:jwtdecode:2.0.2")

    val lifecycle_version = "2.11.0"
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycle_version")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:$lifecycle_version")

    val room_version = "2.8.4"
    implementation("androidx.room:room-runtime:${room_version}")
    ksp("androidx.room:room-compiler:$room_version")
    implementation("androidx.room:room-ktx:${room_version}")

    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.okhttp3:logging-interceptor:5.4.0")

    val nav_version = "2.9.8"
    implementation("androidx.navigation:navigation-fragment-ktx:$nav_version")
    implementation("androidx.navigation:navigation-ui-ktx:$nav_version")
    implementation("androidx.navigation:navigation-dynamic-features-fragment:${nav_version}")
    androidTestImplementation("androidx.navigation:navigation-testing:$nav_version")

    val hilt_version = "2.60.1"
    implementation("com.google.dagger:hilt-android:$hilt_version")
    ksp("com.google.dagger:hilt-android-compiler:$hilt_version")

    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.analytics)
    implementation(libs.firebase.messaging)

    val cameraxVersion = "1.6.1"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-video:$cameraxVersion")
    implementation("androidx.camera:camera-extensions:$cameraxVersion")

}