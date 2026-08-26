plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    jvmToolchain(17)
}

tasks.test {
    useJUnit()
    val testClasses = tasks.compileTestKotlin.flatMap { it.destinationDirectory }
    val mainClasses = tasks.compileKotlin.flatMap { it.destinationDirectory }
    testClassesDirs = files(testClasses)
    classpath = files(testClasses, mainClasses) + configurations.getByName("testRuntimeClasspath")
}

dependencies {
    implementation("javax.inject:javax.inject:1")
    implementation(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.coroutines.test)
}
