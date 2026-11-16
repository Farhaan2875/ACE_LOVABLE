package com.farhaan.projects.Ace.storage;

import java.time.Duration;

/** Where project files live. Two implementations: MinIO (production) and a local folder (easy dev). */
public interface ObjectStorage {
    void put(String key, byte[] data, String contentType);

    /** Throws StorageException if the object does not exist. */
    byte[] get(String key);

    void delete(String key);

    /** A temporary download link that pods use to fetch the code. Only MinIO can do this. */
    default String presignedGetUrl(String key, Duration ttl) {
        throw new UnsupportedOperationException("This storage type cannot create download links - use ace.storage.type=minio");
    }
}
