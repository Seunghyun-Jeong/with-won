package com.withwon.customer.evidence.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Component
public class LocalEvidenceStorage implements EvidenceStorage {

    private final Path root;

    public LocalEvidenceStorage(
            @Value("${withwon.evidence.storage-root:./storage/evidence}") String storageRoot) {
        this.root = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Override
    public StoredEvidence store(MultipartFile file) {
        String storageKey = UUID.randomUUID().toString();
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalStateException("Invalid evidence storage path.");
        }

        try {
            Files.createDirectories(root);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size;
            try (InputStream input = file.getInputStream()) {
                size = copyAndDigest(input, target, digest);
            }
            return new StoredEvidence(storageKey, HexFormat.of().formatHex(digest.digest()), size);
        } catch (IOException e) {
            throw new IllegalStateException("Could not store evidence file.", e);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available.", e);
        }
    }

    @Override
    public Resource load(String storageKey) {
        return new FileSystemResource(resolve(storageKey));
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new IllegalStateException("Could not delete evidence file.", e);
        }
    }

    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("storageKey must not be blank.");
        }
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new IllegalArgumentException("Invalid evidence storage path.");
        }
        return target;
    }

    private long copyAndDigest(InputStream input, Path target, MessageDigest digest) throws IOException {
        byte[] buffer = new byte[8192];
        long size = 0;
        try (var output = Files.newOutputStream(target)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
                size += read;
            }
        }
        return size;
    }
}
