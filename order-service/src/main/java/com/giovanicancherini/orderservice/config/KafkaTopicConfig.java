package com.giovanicancherini.orderservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic ordersTopic(@Value("${app.kafka.topic.orders}") String topic) {
        // 3 partições: paralelismo real no consumidor sem perder ordem por
        // cliente, já que a key (customerId) decide a partição.
        return TopicBuilder.name(topic)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
