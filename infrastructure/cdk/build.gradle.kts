plugins {
    java
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

application {
    mainClass = "com.finflow.infra.FinFlowApp"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("software.amazon.awscdk:aws-cdk-lib:2.147.0")
    implementation("software.constructs:constructs:10.3.0")
    implementation("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    testImplementation("software.amazon.awscdk:aws-cdk-lib:2.147.0")
    testImplementation("junit:junit:4.13.2")
}
