plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    kotlin("multiplatform") version "2.3.0" apply false
    kotlin("plugin.serialization") version "2.3.0" apply false
    kotlin("plugin.compose") version "2.3.0" apply false
    kotlin("android") version "2.3.0" apply false
    id("com.android.application") version "8.9.1" apply false
    id("com.android.library") version "8.9.1" apply false
}
