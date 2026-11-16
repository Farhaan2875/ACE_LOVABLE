package com.farhaan.projects.Ace.storage;

import com.farhaan.projects.Ace.config.AceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration
public class StorageConfig {

    /** ace.storage.type = minio | local */
    @Bean
    public ObjectStorage objectStorage(AceProperties props) {
        AceProperties.Storage cfg = props.getStorage();
        if ("minio".equalsIgnoreCase(cfg.getType())) {
            return new MinioObjectStorage(cfg.getMinio());
        }
        return new LocalObjectStorage(Path.of(cfg.getLocalRoot()));
    }
}
