import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.3")
    }
}

dependencies {
    implementation(libs.bundles.service.common)
    implementation(libs.bundles.persistence)
    implementation(libs.spring.boot.amqp)
    implementation(libs.springdoc.openapi)
    implementation(project(":common"))

    compileOnly(libs.lombok)

    annotationProcessor(libs.lombok)
    annotationProcessor(libs.mapstruct.processor)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.testcontainers.rabbit)
    testImplementation(libs.spring.security.test)
}
