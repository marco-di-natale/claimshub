package it.claimshub.policy_service.model;

import java.util.List;

public record PageResult<T>(List<T> content, int page, int size, long totalElements) {}