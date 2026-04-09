package com.finflow.account.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

/**
 * Kafka producer setup for publishing JSON-valued messages (e.g. audit events).
 * This file teaches Spring Boot how to send messages to Kafka — what server to connect to, how to serialize the data, and how reliable the sending should be.
 Without this config, your AccountEventPublisher would have no KafkaTemplate bean to inject, and sending any message would fail.
 */

@Configuration
@EnableKafka  // without this, @KafkaListener annotations in other classes won't work. It's also good practice on the producer config class to make intent clear.
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}") // // Reads Kafka broker address; env overrides in prod(MSK), fallback to localhost. Bootstrap servers" is Kafka's term for the address of the Kafka broker.
    private String bootstrapServers;


    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class); // convert the message KEY to bytes using StringSerializer so kafka understands"
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class); // Converts Java object (e.g. TransactionEvent) to JSON bytes for Kafka; consumer deserializes back
        // Shared Jackson property for Spring Kafka JSON serde (serializer reads it in configure()).
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(ProducerConfig.ACKS_CONFIG, "all");
        config.put(ProducerConfig.RETRIES_CONFIG, 3);
        config.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        return new DefaultKafkaProducerFactory<>(config);   // Creates Kafka producer with above settings
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }
}

/*
These are the three Spring Kafka classes for sending messages:

ProducerFactory — an interface: a factory that creates Kafka producers. Think of it as a template or blueprint.
DefaultKafkaProducerFactory — the concrete implementation of that factory
KafkaTemplate — the class your services actually call to send messages. Think of it like RestTemplate but for Kafka instead of HTTP.


 */