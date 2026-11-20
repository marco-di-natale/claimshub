package it.claimshub.policy_service.service;

import it.claimshub.policy_service.model.Policy;
import it.claimshub.policy_service.model.PolicyStatus;
import it.claimshub.policy_service.model.PolicyType;
import it.claimshub.policy_service.repository.PolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PolicyLifecycleTest {

    static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-20T10:00:00Z"), ZoneOffset.UTC);

    @Mock
    PolicyRepository repository;
    PolicyService service;

    @BeforeEach
    void setUp() {
        service = new PolicyService(repository, CLOCK);
    }

    private Policy policy(PolicyStatus status, LocalDate end) {
        return new Policy(1L, "POL-0001", "Mario Rossi", PolicyType.AUTO, status,
                new BigDecimal("450.00"), LocalDate.of(2025, 1, 1), end);
    }

    @Test
    void cancel_activePolicy_becomesCancelled() {
        var active = policy(PolicyStatus.ACTIVE, LocalDate.of(2026, 12, 31));
        var cancelled = policy(PolicyStatus.CANCELLED, LocalDate.of(2026, 12, 31));
        when(repository.findById(1L)).thenReturn(Optional.of(active), Optional.of(cancelled));
        when(repository.transition(1L, PolicyStatus.ACTIVE, PolicyStatus.CANCELLED,
                LocalDate.of(2026, 12, 31))).thenReturn(true);

        assertEquals(PolicyStatus.CANCELLED, service.cancel(1L).status());
    }

    @ParameterizedTest
    @EnumSource(value = PolicyStatus.class, names = {"EXPIRED", "CANCELLED"})
    void cancel_nonActivePolicy_isRejected(PolicyStatus status) {
        when(repository.findById(1L)).thenReturn(Optional.of(policy(status, LocalDate.of(2026, 12, 31))));

        assertThrows(InvalidPolicyStateException.class, () -> service.cancel(1L));
        verify(repository, never()).transition(anyLong(), any(), any(), any());
    }

    @Test
    void cancel_missingPolicy_throwsNotFound() {
        when(repository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(PolicyNotFoundException.class, () -> service.cancel(999L));
    }

    @Test
    void renew_activePolicy_extendsByOneYear() {
        var before = policy(PolicyStatus.ACTIVE, LocalDate.of(2026, 12, 31));
        var after = policy(PolicyStatus.ACTIVE, LocalDate.of(2027, 12, 31));
        when(repository.findById(1L)).thenReturn(Optional.of(before), Optional.of(after));
        when(repository.transition(1L, PolicyStatus.ACTIVE, PolicyStatus.ACTIVE,
                LocalDate.of(2027, 12, 31))).thenReturn(true);

        assertEquals(LocalDate.of(2027, 12, 31), service.renew(1L).endDate());
    }

    @Test
    void renew_expiredPolicy_becomesActiveAgain() {
        var before = policy(PolicyStatus.EXPIRED, LocalDate.of(2025, 12, 31));
        var after = policy(PolicyStatus.ACTIVE, LocalDate.of(2026, 12, 31));
        when(repository.findById(1L)).thenReturn(Optional.of(before), Optional.of(after));
        when(repository.transition(1L, PolicyStatus.EXPIRED, PolicyStatus.ACTIVE,
                LocalDate.of(2026, 12, 31))).thenReturn(true);

        assertEquals(PolicyStatus.ACTIVE, service.renew(1L).status());
    }

    @Test
    void renew_cancelledPolicy_isRejected() {
        when(repository.findById(1L)).thenReturn(
                Optional.of(policy(PolicyStatus.CANCELLED, LocalDate.of(2026, 12, 31))));

        assertThrows(InvalidPolicyStateException.class, () -> service.renew(1L));
    }

    @Test
    void renew_whenNewEndWouldStillBeInThePast_isRejected() {
        when(repository.findById(1L)).thenReturn(
                Optional.of(policy(PolicyStatus.EXPIRED, LocalDate.of(2024, 1, 1))));

        assertThrows(InvalidPolicyStateException.class, () -> service.renew(1L));
        verify(repository, never()).transition(anyLong(), any(), any(), any());
    }

    @Test
    void cancel_whenStatusChangedMeanwhile_failsInsteadOfOverwriting() {
        when(repository.findById(1L)).thenReturn(
                Optional.of(policy(PolicyStatus.ACTIVE, LocalDate.of(2026, 12, 31))));
        when(repository.transition(anyLong(), any(), any(), any())).thenReturn(false);

        assertThrows(InvalidPolicyStateException.class, () -> service.cancel(1L));
    }
}