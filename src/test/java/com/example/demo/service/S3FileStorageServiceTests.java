package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3FileStorageServiceTests {

    private final S3FileStorageService storage = new S3FileStorageService(
            objectProvider(mock(S3Client.class)), objectProvider(mock(S3Presigner.class)),
            true, "test-bucket", "", Duration.ofMinutes(10), "./local-uploads");

    private static <T> ObjectProvider<T> objectProvider(T instance) {
        return new ObjectProvider<T>() {
            @Override
            public T getObject() { return instance; }
            @Override
            public T getObject(Object... args) { return instance; }
            @Override
            public T getIfAvailable() { return instance; }
            @Override
            public T getIfUnique() { return instance; }
        };
    }

    @Test
    void convertsLegacyImagePathToObjectKey() {
        assertEquals("receipt/123/a.jpg", storage.toObjectKey("/image/receipt/123/a.jpg"));
        assertEquals("receipt/123/a.jpg", storage.toObjectKey("image/receipt/123/a.jpg"));
    }

    @Test
    void decodesEncodedLegacyPath() {
        assertEquals("receipt/123/a b.jpg", storage.toObjectKey("%2Fimage%2Freceipt%2F123%2Fa%2520b.jpg"));
    }

    @Test
    void rejectsPathTraversal() {
        assertThrows(IllegalArgumentException.class,
                () -> storage.toObjectKey("/image/receipt/../secret.txt"));
    }
}
