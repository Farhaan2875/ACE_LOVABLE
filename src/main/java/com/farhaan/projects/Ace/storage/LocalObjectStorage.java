package com.farhaan.projects.Ace.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Stores objects as plain files under a root folder. Great for development without MinIO. */
public class LocalObjectStorage implements ObjectStorage {
    private final Path root;

    public LocalObjectStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    private Path resolve(String key) {
        Path target = root.resolve(key).normalize();
        if (!target.startsWith(root)) throw new StorageException("Illegal storage key: " + key);   // path traversal guard
        return target;
    }

    @Override
    public void put(String key, byte[] data, String contentType) {
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, data);
        } catch (IOException e) {
            throw new StorageException("Could not write " + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            return Files.readAllBytes(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Object not found: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new StorageException("Could not delete " + key, e);
        }
    }
}
