import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency) apply false
    alias(libs.plugins.protobuf) apply false
}

subprojects {
    pluginManager.withPlugin("java") {
        if (name !in listOf("common", "proto")) {
            val catalog = rootProject.libs
            dependencies.add("compileOnly", catalog.lombok)
            dependencies.add("annotationProcessor", catalog.lombok)
            dependencies.add("annotationProcessor", catalog.mapstruct.processor)
        }
    }

    when (name) {
        "common", "proto" -> apply(plugin = "java-library")
        else -> {
            apply(plugin = "org.springframework.boot")
            apply(plugin = "io.spring.dependency-management")
        }
    }

    tasks.withType<JavaCompile>().configureEach {
        options.compilerArgs.add("--enable-preview")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
        jvmArgs("--enable-preview")
    }

    tasks.withType<JavaExec>().configureEach {
        jvmArgs("--enable-preview")
    }

    afterEvaluate {
        extensions.findByType<JavaPluginExtension>()?.apply {
            toolchain {
                languageVersion.set(JavaLanguageVersion.of(21))
            }
        }

        if (name !in listOf("common", "proto") && pluginManager.hasPlugin("org.springframework.boot")) {
            tasks.findByName("jar")?.let { task ->
                (task as Jar).enabled = false
            }
        }
    }
}
