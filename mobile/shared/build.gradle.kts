plugins {
    kotlin("multiplatform") version "2.3.0"
    kotlin("plugin.serialization") version "2.3.0"
    id("com.android.library")
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
    
    // Suppress deprecation warnings for all targets
    targets.all {
        compilations.all {
            compilerOptions.configure {
                // Don't treat warnings as errors
                allWarningsAsErrors.set(false)
                freeCompilerArgs.addAll(
                    "-Xexpect-actual-classes"
                )
            }
        }
    }
    
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64()
    ).forEach {
        it.binaries.framework {
            baseName = "shared"
            isStatic = true
        }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                
                // Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
                
                // Ktor for networking - Updated to 3.4.0 for Supabase 3.4.1 compatibility
                implementation("io.ktor:ktor-client-core:3.4.0")
                implementation("io.ktor:ktor-client-content-negotiation:3.4.0")
                implementation("io.ktor:ktor-serialization-kotlinx-json:3.4.0")
                
                // DateTime
                api("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")
                
                // Supabase - Updated to 3.4.1 for better session management
                implementation("io.github.jan-tennert.supabase:postgrest-kt:3.4.1")
                implementation("io.github.jan-tennert.supabase:storage-kt:3.4.1")
                implementation("io.github.jan-tennert.supabase:realtime-kt:3.4.1")
                implementation("io.github.jan-tennert.supabase:auth-kt:3.4.1")
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
                implementation("io.kotest:kotest-property:5.8.0")
                implementation("io.kotest:kotest-framework-engine:5.8.0")
            }
        }
        
        val androidMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-android:3.4.0")
                implementation("androidx.security:security-crypto:1.1.0-alpha06")
            }
        }
        
        val iosX64Main by getting
        val iosArm64Main by getting
        val iosSimulatorArm64Main by getting
        val iosMain by creating {
            dependsOn(commonMain)
            iosX64Main.dependsOn(this)
            iosArm64Main.dependsOn(this)
            iosSimulatorArm64Main.dependsOn(this)
            
            dependencies {
                implementation("io.ktor:ktor-client-darwin:3.4.0")
            }
        }
    }
}

android {
    namespace = "com.amigo.shared"
    compileSdk = 34
    
    defaultConfig {
        minSdk = 26
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
