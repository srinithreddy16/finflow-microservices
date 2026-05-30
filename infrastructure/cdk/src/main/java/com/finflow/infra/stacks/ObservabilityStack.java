package com.finflow.infra.stacks;

import com.finflow.infra.FinFlowStack;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ecs.*;
import software.constructs.Construct;
import java.util.List;
import java.util.Map;

public class ObservabilityStack extends FinFlowStack {

    public ObservabilityStack(Construct scope, String id,
                              StackProps props,
                              NetworkStack networkStack,
                              EcsClusterStack ecsStack) {
        super(scope, id, props);

        Cluster cluster = ecsStack.getCluster();

        // Jaeger all-in-one for tracing
        createObservabilityService(cluster, networkStack,
            "jaeger", "jaegertracing/all-in-one:1.57",
            16686, Map.of("COLLECTOR_OTLP_ENABLED", "true"));

        // Prometheus for metrics
        createObservabilityService(cluster, networkStack,
            "prometheus", "prom/prometheus:v2.51.0",
            9090, Map.of());

        // Grafana for dashboards
        createObservabilityService(cluster, networkStack,
            "grafana", "grafana/grafana:10.4.0",
            3000, Map.of(
                "GF_SECURITY_ADMIN_USER", "admin",
                "GF_SECURITY_ADMIN_PASSWORD", "finflow123"));
    }

    private void createObservabilityService(
            Cluster cluster, NetworkStack networkStack,
            String name, String image, int port,
            Map<String, String> env) {

        FargateTaskDefinition taskDef =
            FargateTaskDefinition.Builder.create(this, name + "-task")
                .cpu(256)
                .memoryLimitMiB(512)
                .build();

        taskDef.addContainer(name, ContainerDefinitionOptions.builder()
            .image(ContainerImage.fromRegistry(image))
            .environment(env)
            .portMappings(List.of(PortMapping.builder()
                .containerPort(port)
                .build()))
            .logging(LogDrivers.awsLogs(AwsLogDriverProps.builder()
                .streamPrefix(name)
                .build()))
            .build());

        FargateService.Builder.create(this, name + "-service")
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
