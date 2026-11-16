package com.farhaan.projects.Ace.events;

import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

/** Dev mode: no broker. The handler runs on a background thread, so the chat is never blocked by a deployment. */
@Slf4j
public class InProcessPublisher implements CodeGeneratedPublisher {
    private final ExecutorService executor;
    private final Consumer<CodeGeneratedEvent> handler;

    public InProcessPublisher(ExecutorService executor, Consumer<CodeGeneratedEvent> handler) {
        this.executor = executor;
        this.handler = handler;
    }

    @Override
    public void publish(CodeGeneratedEvent event) {
        executor.submit(() -> {
            try {
                handler.accept(event);
            } catch (Exception e) {
                log.error("code.generated handler failed for project {}", event.projectId(), e);
            }
        });
    }
}
