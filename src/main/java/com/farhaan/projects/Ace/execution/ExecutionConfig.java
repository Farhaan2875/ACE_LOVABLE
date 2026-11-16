package com.farhaan.projects.Ace.execution;

import com.farhaan.projects.Ace.config.AceProperties;
import com.farhaan.projects.Ace.storage.MinioObjectStorage;
import com.farhaan.projects.Ace.storage.ObjectStorage;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutionConfig {

    /** ace.execution.runner = local | kubernetes */
    @Bean
    public PreviewRunner previewRunner(AceProperties props, ObjectStorage storage) {
        AceProperties.Execution cfg = props.getExecution();
        if ("kubernetes".equalsIgnoreCase(cfg.getRunner())) {
            if (!(storage instanceof MinioObjectStorage)) {
                throw new IllegalStateException("The kubernetes runner needs ace.storage.type=minio (pods download code through MinIO links)");
            }
            return new KubectlPreviewRunner(cfg, storage);
        }
        return new LocalProcessRunner(cfg);
    }
}
