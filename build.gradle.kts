plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.room) apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}

subprojects {
    tasks.withType<Test>().configureEach {
        jvmArgs("-Dfile.encoding=UTF-8", "-Dsun.jnu.encoding=UTF-8")
        systemProperty("file.encoding", "UTF-8")
        systemProperty("sun.jnu.encoding", "UTF-8")
        environment("JAVA_TOOL_OPTIONS", "-Dfile.encoding=UTF-8")
    }
}
