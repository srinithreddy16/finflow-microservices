package com.finflow.notification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.sns.SnsClient;

@Configuration
public class AwsConfig {

    @Bean
    public SesClient sesClient(@Value("${aws.region:us-east-1}") String awsRegion) {
        return SesClient.builder().region(Region.of(awsRegion)).build();
    }

    @Bean
    public SnsClient snsClient(@Value("${aws.region:us-east-1}") String awsRegion) {
        return SnsClient.builder().region(Region.of(awsRegion)).build();
    }
}
