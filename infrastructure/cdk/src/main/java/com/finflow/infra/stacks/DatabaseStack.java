package com.finflow.infra.stacks;

import com.finflow.infra.FinFlowStack;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.rds.*;
import software.constructs.Construct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DatabaseStack extends FinFlowStack {

    private final Map<String, DatabaseInstance> databases;

    public DatabaseStack(Construct scope, String id,
                         StackProps props,
                         NetworkStack networkStack) {
        super(scope, id, props);

        String[] serviceNames = {
            "account", "transaction", "payment", "saga",
            "fraud", "notification", "analytics", "report", "keycloak"
        };

        this.databases = new HashMap<>();

        for (String serviceName : serviceNames) {
            DatabaseInstance db = DatabaseInstance.Builder
                .create(this, serviceName + "-db")
                .instanceIdentifier(resourceName(serviceName + "-db"))
                .engine(DatabaseInstanceEngine.postgres(
                    PostgresInstanceEngineProps.builder()
                        .version(PostgresEngineVersion.VER_16_3)
                        .build()))
                .instanceType(software.amazon.awscdk.services.ec2.InstanceType.of(
                    InstanceClass.BURSTABLE3, InstanceSize.SMALL))
                .vpc(networkStack.getVpc())
                .vpcSubnets(SubnetSelection.builder()
                    .subnetType(SubnetType.PRIVATE_ISOLATED)
                    .build())
                .securityGroups(List.of(networkStack.getDbSecurityGroup()))
                .databaseName(serviceName + "_db")
                .multiAz(false) // enable in production
                .allocatedStorage(20)
                .maxAllocatedStorage(100)
                .deletionProtection(false) // enable in production
                .removalPolicy(RemovalPolicy.DESTROY)
                .backupRetention(Duration.days(7))
                .preferredBackupWindow("03:00-04:00")
                .preferredMaintenanceWindow("mon:04:00-mon:05:00")
                .build();

            databases.put(serviceName, db);

            CfnOutput.Builder.create(this,
                    serviceName + "-db-endpoint")
                .value(db.getDbInstanceEndpointAddress())
                .exportName("FinFlow" + capitalize(serviceName) + "DbEndpoint")
                .build();
        }
    }

    public DatabaseInstance getDatabase(String serviceName) {
        return databases.get(serviceName);
    }

    private String capitalize(String s) {
        return s.substring(0, 1).toUpperCase() + s.substring(1);
    }
}
