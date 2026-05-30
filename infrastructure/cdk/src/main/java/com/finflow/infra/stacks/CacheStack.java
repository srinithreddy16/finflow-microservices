package com.finflow.infra.stacks;

import com.finflow.infra.FinFlowStack;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.elasticache.*;
import software.constructs.Construct;
import java.util.List;

public class CacheStack extends FinFlowStack {

    private final CfnReplicationGroup redisCluster;

    public CacheStack(Construct scope, String id,
                      StackProps props,
                      NetworkStack networkStack) {
        super(scope, id, props);

        CfnSubnetGroup subnetGroup = CfnSubnetGroup.Builder
            .create(this, "RedisSubnetGroup")
            .description("Redis subnet group for FinFlow")
            .subnetIds(networkStack.getVpc()
                .selectSubnets(SubnetSelection.builder()
                    .subnetType(SubnetType.PRIVATE_ISOLATED)
                    .build())
                .getSubnetIds())
            .build();

        this.redisCluster = CfnReplicationGroup.Builder
            .create(this, "RedisCluster")
            .replicationGroupDescription("FinFlow Redis cluster")
            .replicationGroupId(resourceName("redis"))
            .cacheNodeType("cache.t3.micro")
            .engine("redis")
            .engineVersion("7.2")
            .numCacheClusters(1)
            .cacheSubnetGroupName(subnetGroup.getRef())
            .securityGroupIds(List.of(
                networkStack.getAppSecurityGroup().getSecurityGroupId()))
            .atRestEncryptionEnabled(true)
            .build();

        CfnOutput.Builder.create(this, "RedisEndpoint")
            .value(redisCluster.getAttrPrimaryEndPointAddress())
            .exportName("FinFlowRedisEndpoint")
            .build();
    }

    public CfnReplicationGroup getRedisCluster() { return redisCluster; }
}
