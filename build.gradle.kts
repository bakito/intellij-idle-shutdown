plugins {
    kotlin("jvm") version "2.4.21"
    id("org.jetbrains.intellij.platform") version "2.19.0"
}

group = "com.github.bakito"
version = "1.0.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdea("2025.2")
        pluginVerifier()
    }
    testImplementation(kotlin("test"))
    // Workaround for IJPL-159134: JUnit5 Test Framework refers to JUnit4 classes
    testRuntimeOnly("junit:junit:4.13.2")
}

tasks.test {
    useJUnitPlatform()
}


intellijPlatform {
    pluginConfiguration {
        ideaVersion { sinceBuild = "252" }
    }
}
