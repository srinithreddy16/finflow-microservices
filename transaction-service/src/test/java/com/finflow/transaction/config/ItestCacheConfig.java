package com.finflow.transaction.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("itest")
@EnableCaching
public class ItestCacheConfig {}
