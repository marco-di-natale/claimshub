package it.claimshub.policy_service.service;

import it.claimshub.policy_service.model.*;
import it.claimshub.policy_service.repository.PolicyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyServiceTest {

    @Mock
    PolicyRepository repository;

    @InjectMocks
    PolicyService service;

    private Policy samplePolicy(Long id) {
        return new Policy(id, "POL-0001", "Mario Rossi", PolicyType.AUTO, PolicyStatus.ACTIVE,
                new BigDecimal("450.00"), LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31));
    }

    private CreatePolicyRequest request(LocalDate start, LocalDate end) {
        return new CreatePolicyRequest("POL-0100", "Test", PolicyType.HOME,
                new BigDecimal("300.00"), start, end);
    }

    @Test
    void findById_returnsPolicy_whenPresent() {
        when(repository.findById(1L)).thenReturn(Optional.of(samplePolicy(1L)));

        Policy result = service.findById(1L);

        assertEquals("POL-0001", result.policyNumber());
    }

    @Test
    void findById_throws_whenMissing() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(PolicyNotFoundException.class, () -> service.findById(999L));
    }

    @Test
    void create_setsStatusActive_andNullId() {
        when(repository.insert(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(request(LocalDate.of(2026, 11, 1), LocalDate.of(2027, 10, 31)));

        ArgumentCaptor<Policy> captor = ArgumentCaptor.forClass(Policy.class);
        verify(repository).insert(captor.capture());
        assertEquals(PolicyStatus.ACTIVE, captor.getValue().status());
        assertNull(captor.getValue().id());
    }

    @Test
    void create_rejectsEndDateBeforeStartDate() {
        var bad = request(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 10, 1));

        assertThrows(IllegalArgumentException.class, () -> service.create(bad));
        verify(repository, never()).insert(any());
    }

    @Test
    void create_rejectsSameStartAndEndDate() {
        var bad = request(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 1));

        assertThrows(IllegalArgumentException.class, () -> service.create(bad));
    }

    @ParameterizedTest
    @CsvSource({"-1,10", "0,0", "0,101"})
    void search_rejectsInvalidPaging(int page, int size) {
        var criteria = new PolicySearchCriteria(null, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class,
                () -> service.search(criteria, page, size, "id", false));
        verifyNoInteractions(repository);
    }

    @Test
    void search_delegatesToRepository_whenPagingIsValid() {
        var criteria = new PolicySearchCriteria(PolicyType.AUTO, null, null, null, null, null, null);
        var page = new PageResult<>(List.of(samplePolicy(1L)), 0, 10, 1);
        when(repository.search(criteria, 0, 10, "id", false)).thenReturn(page);

        PageResult<Policy> result = service.search(criteria, 0, 10, "id", false);

        assertEquals(1, result.totalElements());
    }
}