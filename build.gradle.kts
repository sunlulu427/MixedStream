plugins {
    id("com.android.application") version "8.4.2" apply false
    id("com.android.library") version "8.4.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
}

allprojects {
    repositories {
        maven(url = uri("https://maven.aliyun.com/repository/public"))
        google()
        mavenCentral()
        maven(url = uri("https://jitpack.io"))
    }
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
