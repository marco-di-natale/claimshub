package it.claimshub.policy_service.controller;

import it.claimshub.policy_service.model.MonthlyNewPolicies;
import it.claimshub.policy_service.model.PolicyStatus;
import it.claimshub.policy_service.model.PremiumByType;
import it.claimshub.policy_service.model.PremiumRank;
import it.claimshub.policy_service.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService service;

    @GetMapping("/premiums-by-type")
    public List<PremiumByType> premiumsByType(
            @RequestParam(required = false) PolicyStatus status,
            @RequestParam(defaultValue = "1") int minPolicies) {
        return service.premiumsByType(status, minPolicies);
    }

    @GetMapping("/premium-ranking")
    public List<PremiumRank> premiumRanking() { return service.premiumRanking(); }

    @GetMapping("/monthly-new-policies")
    public List<MonthlyNewPolicies> monthlyNewPolicies() { return service.monthlyNewPolicies(); }
}