package com.finflow.infra;

import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.constructs.Construct;

public class FinFlowStack extends Stack {

    public static final String APP_NAME = "finflow";
    public static final String ENV_NAME = "prod";

    public FinFlowStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);
    }

    protected String resourceName(String name) {
        return APP_NAME + "-" + name + "-" + ENV_NAME;
    }
}
