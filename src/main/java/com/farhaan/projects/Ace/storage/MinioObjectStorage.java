package com.farhaan.projects.Ace.storage;

import com.farhaan.projects.Ace.config.AceProperties;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Duration;

/** MinIO (S3 compatible) storage - the "MinIO Storage" box of the architecture. */
public class MinioObjectStorage implements ObjectStorage {
    private final MinioClient client;
    private final MinioClient presignClient;   // same server, but addressed by the URL that pods can reach
    private final String bucket;
    private volatile boolean bucketReady = false;

    public MinioObjectStorage(AceProperties.Storage.Minio cfg) {
        this.bucket = cfg.getBucket();
        // region is set explicitly so building a presigned URL never needs a network call
        this.client = MinioClient.builder()
                .endpoint(cfg.getEndpoint())
                .credentials(cfg.getAccessKey(), cfg.getSecretKey())
                .region(cfg.getRegion())
                .build();
        this.presignClient = MinioClient.builder()
                .endpoint(cfg.getPublicEndpoint())
                .credentials(cfg.getAccessKey(), cfg.getSecretKey())
                .region(cfg.getRegion())
                .build();
    }

    /** Created lazily so the app can start even when MinIO is still booting. */
    private void ensureBucket() throws Exception {
        if (bucketReady) return;
        synchronized (this) {
            if (!bucketReady) {
                if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) {
                    client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                }
                bucketReady = true;
            }
        }
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        try {
            ensureBucket();
            try (InputStream in = new ByteArrayInputStream(data)) {
                client.putObject(PutObjectArgs.builder()
                        .bucket(bucket).object(key)
                        .stream(in, data.length, -1)
                        .contentType(contentType == null ? "application/octet-stream" : contentType)
                        .build());
            }
        } catch (Exception e) {
            throw new StorageException("MinIO upload failed for " + key + ": " + e.getMessage(), e);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            ensureBucket();
            try (InputStream in = client.getObject(GetObjectArgs.builder().bucket(bucket).object(key).build())) {
                return in.readAllBytes();
            }
        } catch (Exception e) {
            throw new StorageException("MinIO download failed for " + key + ": " + e.getMessage(), e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            ensureBucket();
            client.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(key).build());
        } catch (Exception e) {
            throw new StorageException("MinIO delete failed for " + key + ": " + e.getMessage(), e);
        }
    }

    @Override
    public String presignedGetUrl(String key, Duration ttl) {
        try {
            return presignClient.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                    .method(Method.GET).bucket(bucket).object(key)
                    .expiry((int) ttl.toSeconds())
                    .build());
        } catch (Exception e) {
            throw new StorageException("Could not create download link: " + e.getMessage(), e);
        }
    }
}
