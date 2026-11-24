package it.claimshub.claims_service.controller;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class ClaimsDocumentsApiTest extends AbstractClaimsApiTest {

    // ---------- documenti ----------

    @Test
    void documents_uploadListDownload_roundTrip() {
        String id = createClaim();
        byte[] content = "contenuto finto di un pdf".getBytes(StandardCharsets.UTF_8);

        asAgent().post().uri("/api/claims/{id}/documents", id)
                .body(pdf("prova.pdf", content, MediaType.APPLICATION_PDF))
                .exchange().expectStatus().isCreated()
                .expectBody()
                .jsonPath("$.fileName").isEqualTo("prova.pdf")
                .jsonPath("$.uploadedBy").isEqualTo("agent");

        String docId = (String) asAgent().get().uri("/api/claims/{id}/documents", id)
                .exchange().expectStatus().isOk()
                .expectBody(List.class).returnResult().getResponseBody().stream()
                .map(o -> ((Map<?, ?>) o).get("id")).findFirst().orElseThrow();

        asAgent().get().uri("/api/claims/{id}/documents/{d}", id, docId).exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Content-Type-Options", "nosniff")
                .expectHeader().value("Content-Disposition", h -> assertTrue(h.startsWith("attachment")))
                .expectBody(byte[].class).isEqualTo(content);
    }

    @Test
    void documents_unsupportedType_is400() {
        String id = createClaim();
        asAgent().post().uri("/api/claims/{id}/documents", id)
                .body(pdf("note.txt", "testo".getBytes(StandardCharsets.UTF_8), MediaType.TEXT_PLAIN))
                .exchange().expectStatus().isBadRequest();
    }

    @Test
    void documents_forMissingClaim_is404() {
        asAgent().post().uri("/api/claims/non-esiste/documents")
                .body(pdf("a.pdf", "x".getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_PDF))
                .exchange().expectStatus().isNotFound();
    }

    @Test
    void documents_tooLarge_is413() {
        String id = createClaim();
        asAgent().post().uri("/api/claims/{id}/documents", id)
                .body(pdf("grande.pdf", new byte[6 * 1024 * 1024], MediaType.APPLICATION_PDF))
                .exchange().expectStatus().isEqualTo(413);
    }

    @Test
    void documents_cannotBeDownloadedThroughAnotherClaim() {
        String first = createClaim();
        String second = createClaim();

        asAgent().post().uri("/api/claims/{id}/documents", first)
                .body(pdf("a.pdf", "x".getBytes(StandardCharsets.UTF_8), MediaType.APPLICATION_PDF))
                .exchange().expectStatus().isCreated();
        String docId = (String) asAgent().get().uri("/api/claims/{id}/documents", first)
                .exchange().expectBody(List.class).returnResult().getResponseBody().stream()
                .map(o -> ((Map<?, ?>) o).get("id")).findFirst().orElseThrow();

        asAgent().get().uri("/api/claims/{id}/documents/{d}", second, docId).exchange()
                .expectStatus().isNotFound();
    }
}
