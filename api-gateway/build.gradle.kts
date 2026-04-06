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

// WebFlux only via Spring Cloud Gateway — do not add spring-boot-starter-web (Spring MVC).

dependencyManagement {
    imports {
        mavenBom("org.springframework.cloud:spring-cloud-dependencies:2023.0.3")
    }
}

dependencies {
    implementation(libs.spring.cloud.gateway)
    implementation(libs.spring.cloud.circuitbreaker)
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.redis)
    implementation(libs.spring.boot.security)
    implementation(libs.spring.oauth2.resource.server)
    implementation(libs.micrometer.tracing.otel)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.resilience4j.reactor)
    implementation(project(":common"))

    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    testImplementation(libs.spring.boot.test)
    testImplementation(libs.spring.security.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit)
}
