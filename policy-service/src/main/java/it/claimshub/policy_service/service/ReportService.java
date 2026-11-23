package it.claimshub.policy_service.service;

import it.claimshub.policy_service.model.MonthlyNewPolicies;
import it.claimshub.policy_service.model.PolicyStatus;
import it.claimshub.policy_service.model.PremiumByType;
import it.claimshub.policy_service.model.PremiumRank;
import it.claimshub.policy_service.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository repository;

    public List<PremiumByType> premiumsByType(PolicyStatus status, int minPolicies) {
        if (minPolicies < 1) {
            throw new IllegalArgumentException("minPolicies must be >= 1");
        }
        return repository.premiumsByType(status, minPolicies);
    }

    public List<PremiumRank> premiumRanking() { return repository.premiumRanking(); }

    public List<MonthlyNewPolicies> monthlyNewPolicies() { return repository.monthlyNewPolicies(); }
}