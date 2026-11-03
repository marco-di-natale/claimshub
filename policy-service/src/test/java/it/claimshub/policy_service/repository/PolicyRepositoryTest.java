package it.claimshub.policy_service.repository;

import it.claimshub.policy_service.model.Policy;
import it.claimshub.policy_service.model.PolicySearchCriteria;
import it.claimshub.policy_service.model.PolicyStatus;
import it.claimshub.policy_service.model.PolicyType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PolicyRepositoryTest {

    @Autowired
    PolicyRepository repository;

    private PolicySearchCriteria criteria(PolicyType type, PolicyStatus status,
                                          BigDecimal min, BigDecimal max, String holder) {
        return new PolicySearchCriteria(type, status, min, max, holder, null, null);
    }

    @Test
    void findById_returnsSeededPolicy() {
        Policy p = repository.findById(1L).orElseThrow();

        assertEquals("POL-0001", p.policyNumber());
        assertEquals(PolicyType.AUTO, p.type());
        assertEquals(0, new BigDecimal("450.00").compareTo(p.premium()));
    }

    @Test
    void findById_returnsEmpty_whenMissing() {
        assertTrue(repository.findById(999L).isEmpty());
    }

    @Test
    void search_withoutFilters_returnsAllFive() {
        var result = repository.search(criteria(null, null, null, null, null), 0, 10, "id", false);

        assertEquals(5, result.totalElements());
        assertEquals(5, result.content().size());
    }

    @Test
    void search_byType() {
        var result = repository.search(criteria(PolicyType.AUTO, null, null, null, null), 0, 10, "id", false);

        assertEquals(2, result.totalElements());
    }

    @Test
    void search_byStatusAndMinPremium() {
        var result = repository.search(
                criteria(null, PolicyStatus.ACTIVE, new BigDecimal("300"), null, null), 0, 10, "id", false);

        assertEquals(2, result.totalElements());
    }

    @Test
    void search_byPremiumRange() {
        var result = repository.search(
                criteria(null, null, new BigDecimal("300"), new BigDecimal("500"), null), 0, 10, "id", false);

        assertEquals(2, result.totalElements());
    }

    @Test
    void search_byHolderName_isCaseInsensitive() {
        var result = repository.search(criteria(null, null, null, null, "ros"), 0, 10, "id", false);

        assertEquals(1, result.totalElements());
        assertEquals("Mario Rossi", result.content().get(0).holderName());
    }

    @Test
    void search_paginationReturnsRightSlice_andFullTotal() {
        var result = repository.search(criteria(null, null, null, null, null), 1, 2, "id", false);

        assertEquals(5, result.totalElements());
        assertEquals(2, result.content().size());
        assertEquals("POL-0003", result.content().get(0).policyNumber());
    }

    @Test
    void search_sortsByPremiumDescending() {
        var result = repository.search(criteria(null, null, null, null, null), 0, 10, "premium", true);

        assertEquals("POL-0003", result.content().get(0).policyNumber()); // premio 900
    }

    @Test
    void search_unknownSortColumn_fallsBackToId_insteadOfInjectingSql() {
        var result = repository.search(criteria(null, null, null, null, null),
                0, 10, "id; DROP TABLE policy", false);

        assertEquals(5, result.totalElements());
        assertEquals("POL-0001", result.content().get(0).policyNumber());
    }

    @Test
    void insert_persistsPolicy_withGeneratedId() {
        var toInsert = new Policy(null, "POL-0900", "Test Repo", PolicyType.LIFE, PolicyStatus.ACTIVE,
                new BigDecimal("123.45"), LocalDate.of(2026, 11, 1), LocalDate.of(2027, 10, 31));

        Policy saved = repository.insert(toInsert);

        assertNotNull(saved.id());
        assertEquals("POL-0900", saved.policyNumber());
    }

    @Test
    void insert_duplicatePolicyNumber_throwsDuplicateKey() {
        var dup = new Policy(null, "POL-0001", "Duplicato", PolicyType.AUTO, PolicyStatus.ACTIVE,
                new BigDecimal("100.00"), LocalDate.of(2026, 11, 1), LocalDate.of(2027, 10, 31));

        assertThrows(DuplicateKeyException.class, () -> repository.insert(dup));
    }

    @Test
    void stats_groupsByTypeAndStatus() {
        var stats = repository.stats();

        assertEquals(4, stats.size());
        var homeActive = stats.stream()
                .filter(s -> s.type() == PolicyType.HOME && s.status() == PolicyStatus.ACTIVE)
                .findFirst().orElseThrow();
        assertEquals(2, homeActive.count());
        assertEquals(0, new BigDecimal("590.50").compareTo(homeActive.totalPremium()));
    }
}