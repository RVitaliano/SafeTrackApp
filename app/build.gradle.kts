plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services") // Plugin do Google Services
}

android {
    namespace = "com.example.safetrack"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.example.safetrack"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }

    viewBinding {
        enable = true
    }
}

dependencies {

    // Dependências Padrão do AndroidX e Material Design
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    implementation("androidx.fragment:fragment-ktx:1.8.3") // Mantido o fragment-ktx

    // Dependências de Maps e Localização
    implementation("com.google.android.gms:play-services-location:21.3.0")
    implementation("com.google.android.gms:play-services-maps:19.0.0")

    // Dependências de Teste
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)

    // ----------------------------------------------------------------------
    // 🔥 CORREÇÃO FIREBASE: Uso Limpo do BOM para gerenciar versões
    // ----------------------------------------------------------------------

    // 1. Usa a Plataforma BOM do Firebase (a versão é definida em libs.versions.toml)
    implementation(platform(libs.firebase.bom))

    // 2. Dependências do Firebase KTX (versão gerenciada pelo BOM)
    // ESTAS SÃO NECESSÁRIAS:
    implementation(libs.firebase.analytics.ktx)
    implementation(libs.firebase.auth.ktx) // Essencial para Firebase.auth, corrige o erro 'ktx'

    // 3. Dependência do Google Sign-In (Play Services Auth)
    // Necessária para o fluxo de login do Google. Mantenha a versão explícita.
    implementation("com.google.android.gms:play-services-auth:20.7.0")

    implementation("com.google.firebase:firebase-database-ktx")
}