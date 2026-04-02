rootProject.name = "finflow"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

include(
    "common",
    "proto",
    "api-gateway",
    "graphql-gateway",
    "account-service",
    "transaction-service",
    "payment-service",
    "saga-orchestrator-service",
    "fraud-detection-service",
    "notification-service",
    "analytics-service",
    "report-service",
    "integration-tests"
)
