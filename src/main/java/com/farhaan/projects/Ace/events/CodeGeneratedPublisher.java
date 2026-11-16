package com.farhaan.projects.Ace.events;

/** intelligence-service publishes, execution-service consumes. Two implementations: in-process and Kafka. */
public interface CodeGeneratedPublisher {
    void publish(CodeGeneratedEvent event);
}
