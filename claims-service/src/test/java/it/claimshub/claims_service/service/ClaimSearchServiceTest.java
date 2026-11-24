package it.claimshub.claims_service.service;

import it.claimshub.claims_service.model.Claim;
import it.claimshub.claims_service.model.ClaimSearchCriteria;
import it.claimshub.claims_service.model.ClaimSortField;
import it.claimshub.claims_service.model.PageResult;
import it.claimshub.claims_service.port.ClaimRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClaimSearchServiceTest {

    @Mock
    ClaimRepository repository;
    @InjectMocks
    ClaimQueryService service;

    private ClaimSearchCriteria none() {
        return new ClaimSearchCriteria(null, null, null, null, null, null);
    }

    @ParameterizedTest
    @CsvSource({"-1,10", "0,0", "0,101"})
    void search_rejectsInvalidPaging(int page, int size) {
        StepVerifier.create(service.search(none(), page, size, ClaimSortField.CREATED_AT, true))
                .expectError(InvalidSearchException.class)
                .verify();
        verifyNoInteractions(repository);
    }

    @Test
    void search_rejectsMinGreaterThanMax() {
        var criteria = new ClaimSearchCriteria(null, null,
                new BigDecimal("900"), new BigDecimal("100"), null, null);
        StepVerifier.create(service.search(criteria, 0, 10, ClaimSortField.AMOUNT, false))
                .expectError(InvalidSearchException.class)
                .verify();
        verifyNoInteractions(repository);
    }

    @Test
    void search_rejectsFromAfterTo() {
        var criteria = new ClaimSearchCriteria(null, null, null, null,
                LocalDate.of(2026, 5, 1), LocalDate.of(2026, 4, 1));
        StepVerifier.create(service.search(criteria, 0, 10, ClaimSortField.CREATED_AT, true))
                .expectError(InvalidSearchException.class)
                .verify();
    }

    @Test
    void search_delegatesToRepository_whenValid() {
        var page = new PageResult<Claim>(List.of(), 0, 10, 0);
        when(repository.search(any(), eq(0), eq(10), eq(ClaimSortField.CREATED_AT), eq(true)))
                .thenReturn(Mono.just(page));

        StepVerifier.create(service.search(none(), 0, 10, ClaimSortField.CREATED_AT, true))
                .expectNext(page)
                .verifyComplete();
    }
}