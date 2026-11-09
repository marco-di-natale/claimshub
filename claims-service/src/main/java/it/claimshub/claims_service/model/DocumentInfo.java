package it.claimshub.claims_service.model;

import java.time.Instant;

public record DocumentInfo(String id, String claimId, String fileName, String contentType,
                           long size, Instant uploadedAt, String uploadedBy) {}