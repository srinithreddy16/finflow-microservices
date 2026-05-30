package com.finflow.infra.stacks;

import com.finflow.infra.FinFlowStack;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.constructs.Construct;
import java.util.List;

public class NetworkStack extends FinFlowStack {

    private final Vpc vpc;
    private final SecurityGroup appSecurityGroup;
    private final SecurityGroup dbSecurityGroup;
    private final SecurityGroup ingressSecurityGroup;

    public NetworkStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        // VPC with public and private subnets across 2 AZs
        this.vpc = Vpc.Builder.create(this, "FinFlowVpc")
            .vpcName(resourceName("vpc"))
            .maxAzs(2)
            .natGateways(1) // 1 NAT gateway — reduce cost
            .subnetConfiguration(List.of(
                SubnetConfiguration.builder()
                    .name("Public")
                    .subnetType(SubnetType.PUBLIC)
                    .cidrMask(24)
                    .build(),
                SubnetConfiguration.builder()
                    .name("Private")
                    .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                    .cidrMask(24)
                    .build(),
                SubnetConfiguration.builder()
                    .name("Isolated")
                    .subnetType(SubnetType.PRIVATE_ISOLATED)
                    .cidrMask(24)
                    .build()))
            .build();

        // Application security group (ECS tasks)
        this.appSecurityGroup = SecurityGroup.Builder.create(this, "AppSG")
            .vpc(vpc)
            .securityGroupName(resourceName("app-sg"))
            .description("Security group for FinFlow application services")
            .allowAllOutbound(true)
            .build();

        // Allow inbound HTTP between app services
        appSecurityGroup.addIngressRule(
            appSecurityGroup,
            Port.allTcp(),
            "Allow all traffic between app services");

        // Database security group
        this.dbSecurityGroup = SecurityGroup.Builder.create(this, "DbSG")
            .vpc(vpc)
            .securityGroupName(resourceName("db-sg"))
            .description("Security group for FinFlow databases")
            .allowAllOutbound(false)
            .build();

        // Allow DB access from app security group only
        dbSecurityGroup.addIngressRule(
            appSecurityGroup,
            Port.tcp(5432),
            "Allow PostgreSQL from app services");

        // Ingress security group (ALB)
        this.ingressSecurityGroup = SecurityGroup.Builder.create(this, "IngressSG")
            .vpc(vpc)
            .securityGroupName(resourceName("ingress-sg"))
            .description("Security group for ALB")
            .allowAllOutbound(true)
            .build();

        ingressSecurityGroup.addIngressRule(
            Peer.anyIpv4(), Port.tcp(80), "HTTP from internet");
        ingressSecurityGroup.addIngressRule(
            Peer.anyIpv4(), Port.tcp(443), "HTTPS from internet");

        // Outputs
        CfnOutput.Builder.create(this, "VpcId")
            .value(vpc.getVpcId())
            .exportName("FinFlowVpcId")
            .build();
    }

    public Vpc getVpc() { return vpc; }
    public SecurityGroup getAppSecurityGroup() { return appSecurityGroup; }
    public SecurityGroup getDbSecurityGroup() { return dbSecurityGroup; }
    public SecurityGroup getIngressSecurityGroup() { return ingressSecurityGroup; }
}
