package com.finflow.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * API Gateway — single entry point for all FinFlow REST traffic.
 * Routes requests to downstream services after JWT validation.
 * Uses Spring Cloud Gateway (WebFlux/reactive). Port: 8080.
 */
@SpringBootApplication
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
