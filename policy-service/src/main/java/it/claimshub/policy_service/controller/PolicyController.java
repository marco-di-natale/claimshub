package it.claimshub.policy_service.controller;

import it.claimshub.policy_service.model.*;
import it.claimshub.policy_service.service.PolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService service;

    @GetMapping
    public PageResult<Policy> search(
            @RequestParam(required = false) PolicyType type,
            @RequestParam(required = false) PolicyStatus status,
            @RequestParam(required = false) BigDecimal minPremium,
            @RequestParam(required = false) BigDecimal maxPremium,
            @RequestParam(required = false) String holderName,
            @RequestParam(required = false) LocalDate startFrom,
            @RequestParam(required = false) LocalDate startTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "false") boolean desc) {
        var criteria = new PolicySearchCriteria(type, status, minPremium, maxPremium,
                holderName, startFrom, startTo);
        return service.search(criteria, page, size, sortBy, desc);
    }

    @GetMapping("/stats")
    public List<PolicyStats> stats() { return service.stats(); }

    @GetMapping("/{id}")
    public Policy get(@PathVariable long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Policy create(@Valid @RequestBody CreatePolicyRequest request) {
        return service.create(request);
    }
}