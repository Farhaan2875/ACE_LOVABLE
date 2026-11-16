package com.farhaan.projects.Ace.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Shared beans: typed properties, a virtual-thread executor and one HTTP client for all outgoing calls. */
@Configuration
@EnableConfigurationProperties(AceProperties.class)
public class AppConfig {

    /** Background work (RAG indexing, deployments). Virtual threads = no thread-pool tuning needed. */
    @Bean(destroyMethod = "shutdown")
    public ExecutorService aceExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    /** Used for Ollama, Qdrant and Stripe calls. */
    @Bean
    public HttpClient httpClient() {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }
}
