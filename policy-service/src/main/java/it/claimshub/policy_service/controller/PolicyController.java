package it.claimshub.policy_service.controller;

import it.claimshub.policy_service.model.*;
import it.claimshub.policy_service.service.PolicyService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/policies")
@RequiredArgsConstructor
public class PolicyController {

    private final PolicyService service;

    @GetMapping
    public PageResult<Policy> list(PolicySearchRequest request) {
        return service.search(request.toCriteria(), request.page(), request.size(),
                request.sortBy(), request.desc());
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

    @PatchMapping("/{id}/cancel")
    public Policy cancel(@PathVariable long id) { return service.cancel(id); }

    @PatchMapping("/{id}/renew")
    public Policy renew(@PathVariable long id) { return service.renew(id); }
}