package com.finflow.infra.stacks;

import com.finflow.infra.FinFlowStack;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.msk.*;
import software.constructs.Construct;
import java.util.List;

public class MessagingStack extends FinFlowStack {

    private final CfnCluster kafkaCluster;

    public MessagingStack(Construct scope, String id,
                          StackProps props,
                          NetworkStack networkStack) {
        super(scope, id, props);

        // MSK Kafka cluster (managed Kafka)
        this.kafkaCluster = CfnCluster.Builder.create(this, "KafkaCluster")
            .clusterName(resourceName("kafka"))
            .numberOfBrokerNodes(2)
            .kafkaVersion("3.6.0")
            .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty.builder()
                .instanceType("kafka.t3.small")
                .clientSubnets(networkStack.getVpc()
                    .selectSubnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                        .build())
                    .getSubnetIds()
                    .subList(0, 2))
                .securityGroups(List.of(
                    networkStack.getAppSecurityGroup().getSecurityGroupId()))
                .storageInfo(CfnCluster.StorageInfoProperty.builder()
                    .ebsStorageInfo(CfnCluster.EBSStorageInfoProperty.builder()
                        .volumeSize(20)
                        .build())
                    .build())
                .build())
            .encryptionInfo(CfnCluster.EncryptionInfoProperty.builder()
                .encryptionInTransit(
                    CfnCluster.EncryptionInTransitProperty.builder()
                        .clientBroker("TLS_PLAINTEXT")
                        .inCluster(true)
                        .build())
                .build())
            .build();

        // RabbitMQ via Amazon MQ
        software.amazon.awscdk.services.amazonmq.CfnBroker rabbitmqBroker =
            software.amazon.awscdk.services.amazonmq.CfnBroker.Builder
                .create(this, "RabbitMQBroker")
                .brokerName(resourceName("rabbitmq"))
                .engineType("RABBITMQ")
                .engineVersion("3.13")
                .deploymentMode("SINGLE_INSTANCE")
                .hostInstanceType("mq.t3.micro")
                .publiclyAccessible(false)
                .subnetIds(List.of(networkStack.getVpc()
                    .selectSubnets(SubnetSelection.builder()
                        .subnetType(SubnetType.PRIVATE_WITH_EGRESS)
                        .build())
                    .getSubnetIds().get(0)))
                .securityGroups(List.of(
                    networkStack.getAppSecurityGroup().getSecurityGroupId()))
                .users(List.of(
                    software.amazon.awscdk.services.amazonmq.CfnBroker
                        .UserProperty.builder()
                        .username("finflow")
                        .password("{{resolve:secretsmanager:finflow-rabbitmq:SecretString:password}}")
                        .build()))
                .build();

        CfnOutput.Builder.create(this, "KafkaClusterArn")
            .value(kafkaCluster.getAttrArn())
            .exportName("FinFlowKafkaClusterArn")
            .build();
    }

    public CfnCluster getKafkaCluster() { return kafkaCluster; }
}
