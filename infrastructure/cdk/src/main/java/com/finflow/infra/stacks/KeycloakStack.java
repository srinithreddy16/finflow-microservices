package com.finflow.infra.stacks;

import com.finflow.infra.FinFlowStack;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.iam.*;
import software.constructs.Construct;
import java.util.List;
import java.util.Map;

public class KeycloakStack extends FinFlowStack {

    public KeycloakStack(Construct scope, String id,
                         StackProps props,
                         NetworkStack networkStack,
                         DatabaseStack databaseStack) {
        super(scope, id, props);

        Cluster cluster = Cluster.Builder.create(this, "KeycloakCluster")
            .vpc(networkStack.getVpc())
            .build();

        FargateTaskDefinition taskDef =
            FargateTaskDefinition.Builder.create(this, "KeycloakTask")
                .cpu(512)
                .memoryLimitMiB(1024)
                .build();

        taskDef.addContainer("keycloak", ContainerDefinitionOptions.builder()
            .image(ContainerImage.fromRegistry(
                "quay.io/keycloak/keycloak:24.0.5"))
            .command(List.of("start"))
            .environment(Map.of(
                "KC_DB", "postgres",
                "KC_DB_URL", "jdbc:postgresql://" +
                    databaseStack.getDatabase("keycloak")
                        .getDbInstanceEndpointAddress() +
                    ":5432/keycloak_db",
                "KC_HOSTNAME", "keycloak.finflow.io",
                "KC_HTTP_ENABLED", "true",
                "KC_HEALTH_ENABLED", "true"))
            .portMappings(List.of(PortMapping.builder()
                .containerPort(8080)
                .build()))
            .logging(LogDrivers.awsLogs(AwsLogDriverProps.builder()
                .streamPrefix("keycloak")
                .build()))
            .build());

        FargateService.Builder.create(this, "KeycloakService")
            .cluster(cluster)
            .taskDefinition(taskDef)
            .desiredCount(1)
            .vpcSubnets(SubnetSelection.builder()
                .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                .build())
            .securityGroups(List.of(networkStack.getAppSecurityGroup()))
            .build();
    }
}
