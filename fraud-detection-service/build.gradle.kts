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
    implementation(libs.spring.boot.kafka)
    implementation(libs.grpc.spring.boot.server)
    implementation(libs.grpc.protobuf)
    implementation(libs.grpc.stub)
    implementation(libs.grpc.netty)
    implementation(libs.protobuf.java)
    implementation(libs.resilience4j.spring.boot3)
    implementation(libs.springdoc.openapi)
    implementation(project(":common"))
    implementation(project(":proto"))

    compileOnly(libs.lombok)

    annotationProcessor(libs.lombok)
    annotationProcessor(libs.mapstruct.processor)

    testImplementation(libs.bundles.testing)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.spring.security.test)
    testImplementation("io.grpc:grpc-inprocess:1.65.0")
}
