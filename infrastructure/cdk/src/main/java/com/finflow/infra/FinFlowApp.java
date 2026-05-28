package com.finflow.infra;

import com.finflow.infra.stacks.*;
import software.amazon.awscdk.App;
import software.amazon.awscdk.Environment;
import software.amazon.awscdk.StackProps;

public class FinFlowApp {

    public static void main(String[] args) {
        App app = new App();

        String account = System.getenv().getOrDefault("CDK_DEFAULT_ACCOUNT", "123456789012");
        String region = System.getenv().getOrDefault("CDK_DEFAULT_REGION", "us-east-1");

        Environment env = Environment.builder().account(account).region(region).build();

        StackProps props = StackProps.builder().env(env).build();

        // Deploy stacks in dependency order
        NetworkStack networkStack = new NetworkStack(app, "FinFlowNetworkStack", props);

        DatabaseStack databaseStack =
                new DatabaseStack(app, "FinFlowDatabaseStack", props, networkStack);

        MessagingStack messagingStack =
                new MessagingStack(app, "FinFlowMessagingStack", props, networkStack);

        CacheStack cacheStack = new CacheStack(app, "FinFlowCacheStack", props, networkStack);

        StorageStack storageStack = new StorageStack(app, "FinFlowStorageStack", props);

        KeycloakStack keycloakStack =
                new KeycloakStack(app, "FinFlowKeycloakStack", props, networkStack, databaseStack);

        EcsClusterStack ecsStack =
                new EcsClusterStack(
                        app,
                        "FinFlowEcsClusterStack",
                        props,
                        networkStack,
                        databaseStack,
                        messagingStack,
                        cacheStack,
                        storageStack);

        ObservabilityStack observabilityStack =
                new ObservabilityStack(app, "FinFlowObservabilityStack", props, networkStack, ecsStack);

        app.synth();
    }
}
