package com.finflow.fraud.config;

import net.devh.boot.grpc.server.interceptor.GrpcGlobalServerInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * gRPC server wiring for this service.
 *
 * <p>The Netty gRPC server is auto-configured by {@code grpc-server-spring-boot-starter} from
 * {@code grpc.server.port} in {@code application.yml} (default {@code 9001}). This configuration
 * only registers cross-cutting interceptors.
 */
@Configuration
public class GrpcServerConfig {

    @Bean
    @GrpcGlobalServerInterceptor
    public LogGrpcInterceptor logGrpcInterceptor() {
        return new LogGrpcInterceptor();
    }
}
