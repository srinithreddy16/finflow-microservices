plugins {
    `java-library`
}

dependencies {
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)

    implementation(platform("org.springframework.boot:spring-boot-dependencies:${libs.versions.spring.boot.get()}"))
    implementation("org.springframework:spring-web")
    implementation("org.springframework.data:spring-data-commons")
    implementation("com.fasterxml.jackson.core:jackson-annotations")

    testImplementation(libs.spring.boot.test)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.junit)
}
