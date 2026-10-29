package it.claimshub.policy_service.service;

import it.claimshub.policy_service.model.*;
import it.claimshub.policy_service.repository.PolicyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final PolicyRepository repository;

    public List<Policy> findAll() {
        return repository.findAll();
    }

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
}