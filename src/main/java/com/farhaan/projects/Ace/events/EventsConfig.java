package com.farhaan.projects.Ace.events;

import com.farhaan.projects.Ace.config.AceProperties;
import com.farhaan.projects.Ace.execution.ExecutionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;

@Configuration
public class EventsConfig {

    /** ace.events.type = inprocess | kafka. Both feed ExecutionService.onCodeGenerated(). */
    @Bean
    public CodeGeneratedPublisher codeGeneratedPublisher(AceProperties props, ExecutorService aceExecutor, ExecutionService executionService) {
        if ("kafka".equalsIgnoreCase(props.getEvents().getType())) {
            return new KafkaCodeGeneratedPublisher(props.getEvents().getKafka(), executionService::onCodeGenerated);
        }
        return new InProcessPublisher(aceExecutor, executionService::onCodeGenerated);
    }
}
