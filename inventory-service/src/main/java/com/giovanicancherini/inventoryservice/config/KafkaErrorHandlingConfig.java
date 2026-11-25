package com.giovanicancherini.inventoryservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.LinkedHashMap;
import java.util.Map;

@Configuration
public class KafkaErrorHandlingConfig {

    private final String deadLetterTopic;

    public KafkaErrorHandlingConfig(@Value("${app.kafka.topic.orders-dead-letter}") String deadLetterTopic) {
        this.deadLetterTopic = deadLetterTopic;
    }

    // O DLT é deste serviço (ADR 1, tópico com dono). Mesmas 3 partições do orders,
    // porque a mensagem vai para a mesma partição de onde veio.
    @Bean
    public NewTopic ordersDeadLetterTopic() {
        return TopicBuilder.name(deadLetterTopic)
                .partitions(3)
                .replicas(1)
                .build();
    }

    // 3 tentativas com 1s entre elas; depois, a mensagem vai para o DLT com o motivo nos headers
    // e a partição segue. Erro de desserialização não é retentável e vai direto.
    @Bean
    public DefaultErrorHandler errorHandler(ProducerFactory<?, ?> producerFactory) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                deadLetterTemplate(producerFactory),
                (record, exception) -> new TopicPartition(deadLetterTopic, record.partition()));
        return new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2));
    }

    // Template próprio, fora do contexto, para não substituir o KafkaTemplate do Spring Boot.
    // Mensagem que não desserializou segue em bytes crus; evento segue em JSON, e o header
    // de tipo original (created, updated, cancelled) é copiado, então dá para reprocessar.
    private KafkaTemplate<String, Object> deadLetterTemplate(ProducerFactory<?, ?> producerFactory) {
        Map<Class<?>, Serializer<?>> serializers = new LinkedHashMap<>();
        serializers.put(byte[].class, new ByteArraySerializer());
        serializers.put(Object.class, new JacksonJsonSerializer<>().noTypeInfo());
        return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(
                producerFactory.getConfigurationProperties(),
                new StringSerializer(),
                new DelegatingByTypeSerializer(serializers, true)));
    }
}
