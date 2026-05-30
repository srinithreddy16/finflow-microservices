package com.finflow.infra.stacks;

import com.finflow.infra.FinFlowStack;
import software.amazon.awscdk.*;
import software.amazon.awscdk.services.s3.*;
import software.amazon.awscdk.services.s3.LifecycleRule;
import software.constructs.Construct;
import java.util.List;

public class StorageStack extends FinFlowStack {

    private final Bucket reportsBucket;

    public StorageStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        this.reportsBucket = Bucket.Builder.create(this, "ReportsBucket")
            .bucketName(resourceName("reports") + "-" +
                Stack.of(this).getAccount())
            .encryption(BucketEncryption.S3_MANAGED)
            .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
            .enforceSSL(true)
            .versioned(false)
            .removalPolicy(RemovalPolicy.RETAIN)
            .lifecycleRules(List.of(
                LifecycleRule.builder()
                    .id("expire-old-reports")
                    .expiration(Duration.days(90))
                    .build()))
            .cors(List.of(CorsRule.builder()
                .allowedMethods(List.of(HttpMethods.GET))
                .allowedOrigins(List.of("https://app.finflow.io"))
                .allowedHeaders(List.of("*"))
                .maxAge(3600)
                .build()))
            .build();

        CfnOutput.Builder.create(this, "ReportsBucketName")
            .value(reportsBucket.getBucketName())
            .exportName("FinFlowReportsBucketName")
            .build();
    }

    public Bucket getReportsBucket() { return reportsBucket; }
}
