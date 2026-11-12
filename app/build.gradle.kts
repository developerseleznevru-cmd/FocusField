plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.ksp)
}

android {
    namespace = "ru.seleznev.focusfield"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "ru.seleznev.focusfield"
        // ТЗ требовало API 21, но весь современный стек AndroidX (navigation 2.10,
        // activity 1.13 и т.д.) требует minSdk 24. Отклонение задокументировано в README.
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
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
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

// Экспорт схемы Room для миграций и тестов
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.incremental", "true")
}

dependencies {
    // Основные библиотеки Android
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.material)
    implementation(libs.androidx.navigation.fragment.ktx)
    implementation(libs.androidx.navigation.ui.ktx)

    // MVVM: ViewModel + LiveData (архитектурные компоненты)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.livedata.ktx)

    // Корутины
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    // Локальная база данных (Room поверх SQLite)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Биометрия: CameraX + ML Kit (распознавание лица, глаз, моргания)
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.mlkit.face.detection)

    // Wear OS (пульс и активность с часов)
    implementation(libs.play.services.wearable)

    // Аутентификация и облако (Firebase)
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // REST API (Retrofit + Gson)
    implementation(libs.retrofit)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.gson)

    // Фоновые задачи: напоминания, синхронизация
    implementation(libs.androidx.work.runtime.ktx)

    // Безопасность: шифрование локальных настроек (Android Keystore)
    implementation(libs.androidx.security.crypto)

    // Настройки приложения
    implementation(libs.androidx.preference.ktx)

    // Графики и диаграммы
    implementation(libs.mpandroidchart)

    // Тесты
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
