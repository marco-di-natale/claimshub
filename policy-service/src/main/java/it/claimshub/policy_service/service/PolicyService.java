package it.claimshub.policy_service.service;

import it.claimshub.policy_service.model.*;
import it.claimshub.policy_service.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository repository;
    private final Clock clock;

    public Policy findById(long id) {
        return repository.findById(id).orElseThrow(() -> new PolicyNotFoundException(id));
    }

    public Policy create(CreatePolicyRequest r) {
        if (!r.endDate().isAfter(r.startDate())) {
            throw new IllegalArgumentException("endDate must be after startDate");
        }
        return repository.insert(new Policy(null, r.policyNumber(), r.holderName(),
                r.type(), PolicyStatus.ACTIVE, r.premium(), r.startDate(), r.endDate()));
    }

    public PageResult<Policy> search(PolicySearchCriteria criteria, int page, int size,
                                     String sortBy, boolean desc) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException("page must be >= 0 and size between 1 and 100");
        }
        return repository.search(criteria, page, size, sortBy, desc);
    }

    public List<PolicyStats> stats() {
        return repository.stats();
    }

    public Policy cancel(long id) {
        Policy policy = findById(id);
        if (!policy.status().canBeCancelled()) {
            throw new InvalidPolicyStateException(
                    "Only ACTIVE policies can be cancelled (current: " + policy.status() + ")");
        }
        apply(policy, PolicyStatus.CANCELLED, policy.endDate());
        return findById(id);
    }

    public Policy renew(long id) {
        Policy policy = findById(id);
        if (!policy.status().canBeRenewed()) {
            throw new InvalidPolicyStateException(
                    "Cancelled policies cannot be renewed (current: " + policy.status() + ")");
        }
        LocalDate newEnd = policy.endDate().plusYears(1);
        if (!newEnd.isAfter(LocalDate.now(clock))) {
            throw new InvalidPolicyStateException("Renewal would still end in the past: " + newEnd);
        }
        apply(policy, PolicyStatus.ACTIVE, newEnd);
        return findById(id);
    }

    private void apply(Policy policy, PolicyStatus newStatus, LocalDate newEnd) {
        if (!repository.transition(policy.id(), policy.status(), newStatus, newEnd)) {
            throw new InvalidPolicyStateException("Policy was modified concurrently, retry");
        }
    }
}