package com.example.demo.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class S3FileStorageServiceTests {

    private final S3FileStorageService storage = new S3FileStorageService(
            mock(S3Client.class), mock(S3Presigner.class), "test-bucket", "", Duration.ofMinutes(10));

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
