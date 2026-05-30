package com.finflow.infra.stacks;

import com.finflow.infra.FinFlowStack;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.patterns.*;
import software.amazon.awscdk.services.ecr.Repository;
import software.amazon.awscdk.services.iam.*;
import software.constructs.Construct;
import java.util.List;
import java.util.Map;

public class EcsClusterStack extends FinFlowStack {

    private final Cluster cluster;

    public EcsClusterStack(Construct scope, String id,
                           StackProps props,
                           NetworkStack networkStack,
                           DatabaseStack databaseStack,
                           MessagingStack messagingStack,
                           CacheStack cacheStack,
                           StorageStack storageStack) {
        super(scope, id, props);

        // ECS Cluster
        this.cluster = Cluster.Builder.create(this, "FinFlowCluster")
            .clusterName(resourceName("cluster"))
            .vpc(networkStack.getVpc())
            .containerInsights(true)
            .build();

        // Task execution role
        Role executionRole = Role.Builder.create(this, "TaskExecutionRole")
            .roleName(resourceName("task-execution-role"))
            .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
            .managedPolicies(List.of(
                ManagedPolicy.fromAwsManagedPolicyName(
                    "service-role/AmazonECSTaskExecutionRolePolicy")))
            .build();

        // Task role (what the container can do)
        Role taskRole = Role.Builder.create(this, "TaskRole")
            .roleName(resourceName("task-role"))
            .assumedBy(new ServicePrincipal("ecs-tasks.amazonaws.com"))
            .build();

        // Grant S3 access for report-service
        storageStack.getReportsBucket().grantReadWrite(taskRole);

        // API Gateway service
        createFargateService("api-gateway", 8080,
            512, 1024, 2, cluster, networkStack,
            executionRole, taskRole, Map.of(
                "SPRING_PROFILES_ACTIVE", "prod"));

        // Transaction service (higher resources)
        createFargateService("transaction-service", 8083,
            1024, 2048, 3, cluster, networkStack,
            executionRole, taskRole, Map.of(
                "SPRING_PROFILES_ACTIVE", "prod"));

        // Payment service (higher resources)
        createFargateService("payment-service", 8084,
            1024, 2048, 3, cluster, networkStack,
            executionRole, taskRole, Map.of());

        // All other services with standard resources (512 cpu, 1024 memory)
        String[] standardServices = {
            "graphql-gateway", "account-service",
            "saga-orchestrator-service", "fraud-detection-service",
            "notification-service", "analytics-service", "report-service"
        };

        int[] ports = { 8081, 8082, 8085, 8086, 8087, 8088, 8089 };

        for (int i = 0; i < standardServices.length; i++) {
            createFargateService(standardServices[i], ports[i],
                512, 1024, 2, cluster, networkStack,
                executionRole, taskRole, Map.of());
        }

        CfnOutput.Builder.create(this, "ClusterName")
            .value(cluster.getClusterName())
            .exportName("FinFlowClusterName")
            .build();
    }

    private FargateService createFargateService(
            String serviceName, int port,
            int cpu, int memoryMiB, int desiredCount,
            Cluster cluster, NetworkStack networkStack,
            Role executionRole, Role taskRole,
            Map<String, String> extraEnv) {

        TaskDefinition taskDef = FargateTaskDefinition.Builder
            .create(this, serviceName + "-task-def")
            .family(resourceName(serviceName))
            .cpu(cpu)
            .memoryLimitMiB(memoryMiB)
            .executionRole(executionRole)
            .taskRole(taskRole)
            .build();

        ContainerDefinitionOptions containerOptions =
            ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromEcrRepository(
                    Repository.fromRepositoryName(this,
                        serviceName + "-repo",
                        "finflow/" + serviceName)))
                .environment(extraEnv)
                .logging(LogDrivers.awsLogs(AwsLogDriverProps.builder()
                    .streamPrefix(serviceName)
                    .build()))
                .portMappings(List.of(PortMapping.builder()
                    .containerPort(port)
                    .build()))
                .healthCheck(HealthCheck.builder()
                    .command(List.of("CMD-SHELL",
                        "curl -f http://localhost:" + port +
                        "/actuator/health || exit 1"))
                    .interval(Duration.seconds(30))
                    .timeout(Duration.seconds(5))
                    .retries(3)
                    .startPeriod(Duration.seconds(60))
                    .build())
                .build();

        taskDef.addContainer(serviceName + "-container", containerOptions);

        return FargateService.Builder.create(this, serviceName + "-service")
            .serviceName(resourceName(serviceName))
            .cluster(cluster)
            .taskDefinition(taskDef)
            .desiredCount(desiredCount)
            .vpcSubnets(SubnetSelection.builder()
                .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                .build())
            .securityGroups(List.of(networkStack.getAppSecurityGroup()))
            .assignPublicIp(false)
            .build();
    }

    public Cluster getCluster() { return cluster; }
}
