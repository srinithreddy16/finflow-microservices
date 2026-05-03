import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    java
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency)
}

version = "1.0.0"

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
    implementation(libs.spring.boot.kafka)
    implementation(libs.keycloak.admin.client)
    implementation(libs.opentelemetry.otlp)
    implementation(libs.mapstruct)
    implementation(libs.springdoc.openapi)
    implementation(project(":common"))

    compileOnly(libs.lombok)
    // Lombok must run before MapStruct so generated getters/setters are visible to MapStruct.
    annotationProcessor(libs.lombok)
    annotationProcessor(libs.mapstruct.processor)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.testcontainers.rabbit)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.testcontainers.keycloak)
    testImplementation(libs.spring.security.test)
}
