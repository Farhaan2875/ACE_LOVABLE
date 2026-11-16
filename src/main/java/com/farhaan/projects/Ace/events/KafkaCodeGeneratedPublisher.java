package com.farhaan.projects.Ace.events;

import com.farhaan.projects.Ace.config.AceProperties;
import com.farhaan.projects.Ace.util.Json;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

/**
 * The Kafka box of the architecture. publish() writes to the "code.generated" topic;
 * a background thread reads the topic and hands every event to the execution service.
 * (In a real split into microservices the consumer would live in the execution-service.)
 */
@Slf4j
public class KafkaCodeGeneratedPublisher implements CodeGeneratedPublisher, AutoCloseable {
    private final String topic;
    private final KafkaProducer<String, String> producer;
    private final Thread consumerThread;
    private volatile boolean running = true;

    public KafkaCodeGeneratedPublisher(AceProperties.Events.Kafka cfg, Consumer<CodeGeneratedEvent> handler) {
        this.topic = cfg.getTopic();

        Properties producerProps = new Properties();
        producerProps.put("bootstrap.servers", cfg.getBootstrapServers());
        producerProps.put("key.serializer", StringSerializer.class.getName());
        producerProps.put("value.serializer", StringSerializer.class.getName());
        producerProps.put("acks", "all");
        this.producer = new KafkaProducer<>(producerProps);

        Properties consumerProps = new Properties();
        consumerProps.put("bootstrap.servers", cfg.getBootstrapServers());
        consumerProps.put("group.id", cfg.getGroupId());
        consumerProps.put("key.deserializer", StringDeserializer.class.getName());
        consumerProps.put("value.deserializer", StringDeserializer.class.getName());
        consumerProps.put("auto.offset.reset", "latest");

        this.consumerThread = Thread.ofVirtual().name("kafka-code-generated").start(() -> consumeLoop(consumerProps, handler));
    }

    @Override
    public void publish(CodeGeneratedEvent event) {
        ObjectNode json = Json.mapper().createObjectNode();
        json.put("projectId", event.projectId());
        json.put("userId", event.userId());
        var pathsArray = json.putArray("paths");
        event.paths().forEach(pathsArray::add);
        // key = projectId keeps all events of one project in order (same partition)
        producer.send(new ProducerRecord<>(topic, String.valueOf(event.projectId()), json.toString()), (meta, error) -> {
            if (error != null) log.error("Could not publish code.generated for project {}", event.projectId(), error);
        });
    }

    private void consumeLoop(Properties props, Consumer<CodeGeneratedEvent> handler) {
        while (running) {
            try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                consumer.subscribe(List.of(topic));
                while (running) {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(1));
                    for (ConsumerRecord<String, String> record : records) {
                        try {
                            JsonNode json = Json.read(record.value());
                            List<String> paths = new ArrayList<>();
                            json.path("paths").forEach(p -> paths.add(p.asText()));
                            handler.accept(new CodeGeneratedEvent(json.path("projectId").asLong(), json.path("userId").asLong(), paths));
                        } catch (Exception e) {
                            log.error("Bad code.generated message skipped: {}", e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                if (!running) break;
                log.warn("Kafka consumer error ({}), retrying in 5s", e.getMessage());
                try { Thread.sleep(5000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            }
        }
    }

    @Override
    public void close() {
        running = false;
        producer.close(Duration.ofSeconds(3));
        consumerThread.interrupt();
    }
}
