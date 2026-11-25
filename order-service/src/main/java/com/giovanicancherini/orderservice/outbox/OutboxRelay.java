package com.giovanicancherini.orderservice.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.mapping.AbstractJavaTypeMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutionException;

// Publica no Kafka o que está pendente no outbox, em ordem de criação, e marca como enviado.
// Se o Kafka falhar, a transação desfaz as marcações e as linhas voltam na próxima rodada.
// Se o processo cair depois do envio e antes do commit, a linha é publicada de novo:
// entrega pelo menos uma vez, e o consumidor deduplica pelo eventId (ADR 7).
@Component
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String topic;

    public OutboxRelay(OutboxEventRepository outboxEventRepository,
                       KafkaTemplate<String, String> kafkaTemplate,
                       @Value("${app.kafka.topic.orders}") String topic) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.topic = topic;
    }

    // ponytail: polling a cada 500ms; CDC (Debezium) se a latência ou a carga no banco pesarem
    @Scheduled(fixedDelayString = "${app.outbox.poll-interval-ms:500}")
    @Transactional
    public void publishPending() throws ExecutionException, InterruptedException {
        for (OutboxEvent event : outboxEventRepository.lockPending(BATCH_SIZE)) {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, event.getMessageKey(), event.getPayload());
            record.headers().add(AbstractJavaTypeMapper.DEFAULT_CLASSID_FIELD_NAME,
                    event.getType().getBytes(StandardCharsets.UTF_8));
            // Espera o ack antes do próximo: mantém a ordem por cliente e só marca o que o broker confirmou.
            kafkaTemplate.send(record).get();
            event.markSent();
        }
    }
}
