package com.farhaan.projects.Ace.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * All custom settings (prefix "ace" in application.yaml) in one typed place.
 * Spring fills these fields automatically from the yaml file / environment variables.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "ace")
public class AceProperties {

    private String frontendUrl = "http://localhost:5173";
    private Cors cors = new Cors();
    private Security security = new Security();
    private Llm llm = new Llm();
    private Rag rag = new Rag();
    private Storage storage = new Storage();
    private Events events = new Events();
    private Execution execution = new Execution();
    private Billing billing = new Billing();

    @Getter @Setter
    public static class Cors {
        private List<String> allowedOrigins = List.of("http://localhost:5173");
    }

    @Getter @Setter
    public static class Security {
        private String jwtSecret = "change-me-change-me-change-me-change-me-please";
        private long jwtExpirationMinutes = 1440;
    }

    @Getter @Setter
    public static class Llm {
        private String baseUrl = "http://localhost:11434";
        private String model = "qwen2.5-coder:7b";
        private double temperature = 0.2;
        private int numCtx = 16384;
        private int numPredict = 8192;
        private boolean toolsEnabled = true;
        private Boolean think = null;          // null = don't send the field (set false to silence qwen3 "thinking")
        private int maxToolRounds = 4;
        private int historyMessages = 10;
    }

    @Getter @Setter
    public static class Rag {
        private boolean enabled = true;
        private String qdrantUrl = "http://localhost:6333";
        private String apiKey = "";
        private String collection = "ace_code";
        private String embeddingModel = "nomic-embed-text";
        private int vectorSize = 768;
        private int topK = 6;
        private int chunkLines = 60;
        private int chunkOverlap = 10;
    }

    @Getter @Setter
    public static class Storage {
        private String type = "local";                 // local | minio
        private String localRoot = "./data/projects";
        private Minio minio = new Minio();

        @Getter @Setter
        public static class Minio {
            private String endpoint = "http://localhost:9002";
            private String publicEndpoint = "http://localhost:9002";
            private String accessKey = "minioadmin";
            private String secretKey = "minioadmin";
            private String bucket = "ace-projects";
            private String region = "us-east-1";
        }
    }

    @Getter @Setter
    public static class Events {
        private String type = "inprocess";             // inprocess | kafka
        private Kafka kafka = new Kafka();

        @Getter @Setter
        public static class Kafka {
            private String bootstrapServers = "localhost:9092";
            private String topic = "code.generated";
            private String groupId = "ace-execution";
        }
    }

    @Getter @Setter
    public static class Execution {
        private String runner = "local";               // local | kubernetes
        private boolean autoPreview = true;
        private int startupTimeoutSeconds = 240;
        private Local local = new Local();
        private Kubernetes kubernetes = new Kubernetes();

        @Getter @Setter
        public static class Local {
            private String workDir = "./data/previews";
            private int portStart = 4100;
            private int portEnd = 4199;
            private String publicHost = "localhost";
        }

        @Getter @Setter
        public static class Kubernetes {
            private String kubectl = "kubectl";
            private String namespacePrefix = "ace-p-";
            private String previewDomain = "preview.localtest.me";
            private String scheme = "http";
            private String ingressClass = "nginx";
            private String nodeImage = "node:22-alpine";
            private String cpuLimit = "1";
            private String memoryLimit = "1Gi";
        }
    }

    @Getter @Setter
    public static class Billing {
        private boolean devMode = false;
        private Stripe stripe = new Stripe();

        @Getter @Setter
        public static class Stripe {
            private String secretKey = "";
            private String webhookSecret = "";
            private String pricePro = "";
            private String priceTeam = "";
        }
    }
}
