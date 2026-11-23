package it.claimshub.policy_service.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import it.claimshub.policy_service.model.PolicyStatus;
import it.claimshub.policy_service.model.PolicyType;
import it.claimshub.policy_service.model.PremiumRank;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ReportRepositoryTest {

    @Autowired
    ReportRepository repo;

    @Test
    void premiumsByType_aggregatesPerType() {
        var rows = repo.premiumsByType(PolicyStatus.ACTIVE, 1);

        var home = rows.stream()
                .filter(r -> r.type() == PolicyType.HOME)
                .findFirst().orElseThrow();
        assertEquals(2, home.policies());
        assertEquals(0, new BigDecimal("590.50").compareTo(home.totalPremium()));
        assertEquals(0, new BigDecimal("295.25").compareTo(home.averagePremium()));
        assertEquals(0, new BigDecimal("280.50").compareTo(home.minPremium()));
        assertEquals(0, new BigDecimal("310.00").compareTo(home.maxPremium()));
    }

    @Test
    void premiumsByType_isOrderedByTotalDescending() {
        var rows = repo.premiumsByType(null, 1);

        for (int i = 1; i < rows.size(); i++) {
            assertTrue(rows.get(i - 1).totalPremium()
                    .compareTo(rows.get(i).totalPremium()) >= 0);
        }
    }

    @Test
    void premiumsByType_having_dropsSmallGroups() {
        // con i dati di partenza solo HOME ha almeno 2 polizze attive
        var rows = repo.premiumsByType(PolicyStatus.ACTIVE, 2);

        assertEquals(1, rows.size());
        assertEquals(PolicyType.HOME, rows.get(0).type());
    }

    @Test
    void premiumRanking_ranksWithinTypeAndExcludesCancelled() {
        var rows = repo.premiumRanking();

        // POL-0004 è annullata: non deve comparire
        assertTrue(rows.stream().noneMatch(r -> r.policyNumber().equals("POL-0004")));

        var home = rows.stream().filter(r -> r.type() == PolicyType.HOME).toList();
        assertEquals("POL-0005", home.get(0).policyNumber());   // 310 > 280.50
        assertEquals(1, home.get(0).rank());
        assertEquals(2, home.get(1).rank());
        assertEquals(0, new BigDecimal("52.50").compareTo(home.get(0).sharePercent()));
        assertEquals(0, new BigDecimal("47.50").compareTo(home.get(1).sharePercent()));
    }

    @Test
    void premiumRanking_sharesOfEachTypeSumTo100() {
        var rows = repo.premiumRanking();

        for (PolicyType t : PolicyType.values()) {
            BigDecimal sum = rows.stream()
                    .filter(r -> r.type() == t)
                    .map(PremiumRank::sharePercent)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.signum() != 0) {
                assertEquals(0, new BigDecimal("100")
                        .compareTo(sum.setScale(0, RoundingMode.HALF_UP)));
            }
        }
    }

    @Test
    void monthlyNewPolicies_hasRunningTotal() {
        var rows = repo.monthlyNewPolicies();

        assertEquals(5, rows.size());
        assertEquals(2024, rows.get(0).year());
        assertEquals(1, rows.get(0).month());
        assertEquals(0, new BigDecimal("900.00").compareTo(rows.get(0).cumulativePremium()));
        // l'ultimo cumulato è la somma di tutti i premi dei dati di partenza
        assertEquals(0, new BigDecimal("2460.50")
                .compareTo(rows.get(rows.size() - 1).cumulativePremium()));
    }
}