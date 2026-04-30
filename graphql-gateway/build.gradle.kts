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
    implementation(libs.spring.boot.graphql)
    implementation(libs.spring.boot.websocket)
    implementation(libs.spring.boot.web)
    implementation(libs.spring.boot.webflux)
    implementation(libs.spring.boot.actuator)
    implementation(libs.spring.boot.security)
    implementation(libs.spring.oauth2.resource.server)
    implementation(libs.spring.boot.redis)
    implementation(libs.micrometer.tracing.otel)
    implementation(libs.micrometer.registry.prometheus)
    implementation(libs.springdoc.openapi)
    implementation(libs.lombok)
    implementation(project(":common"))

    compileOnly(libs.lombok)

    annotationProcessor(libs.lombok)

    testImplementation(libs.spring.boot.test)
    testImplementation("org.springframework.graphql:spring-graphql-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation(libs.spring.security.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit)
}
