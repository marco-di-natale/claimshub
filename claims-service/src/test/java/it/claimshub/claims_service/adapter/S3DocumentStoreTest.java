package it.claimshub.claims_service.adapter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.test.StepVerifier;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class S3DocumentStoreTest {

    @Mock
    S3Client s3;
    S3DocumentStore store;

    @BeforeEach
    void setUp() {
        store = new S3DocumentStore(s3, "bucket-di-prova");
    }

    @Test
    void put_sendsObjectToConfiguredBucketAndKey() {
        StepVerifier.create(store.put("k1", "ciao".getBytes())).verifyComplete();

        ArgumentCaptor<PutObjectRequest> captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(s3).putObject(captor.capture(), any(RequestBody.class));
        assertEquals("bucket-di-prova", captor.getValue().bucket());
        assertEquals("k1", captor.getValue().key());
    }

    @Test
    void get_returnsBytes() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(
                ResponseBytes.fromByteArray(GetObjectResponse.builder().build(), "ciao".getBytes()));

        StepVerifier.create(store.get("k1"))
                .assertNext(b -> assertArrayEquals("ciao".getBytes(), b))
                .verifyComplete();
    }

    @Test
    void get_missingKey_isEmptyMono() {
        when(s3.getObjectAsBytes(any(GetObjectRequest.class)))
                .thenThrow(NoSuchKeyException.builder().build());

        StepVerifier.create(store.get("non-esiste")).verifyComplete();
    }
}