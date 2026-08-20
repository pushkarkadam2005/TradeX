package com.tradex.ledger;

import com.tradex.ledger.entity.LedgerEntry;
import com.tradex.ledger.repository.LedgerRepository;
import com.tradex.ledger.service.LedgerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock
    private LedgerRepository ledgerRepository;

    @Test
    @DisplayName("Record Ledger Entry — Creates immutable double-entry record with balance_before and balance_after")
    void recordLedgerEntrySuccess() {
        LedgerService ledgerService = new LedgerService(ledgerRepository);
        UUID transactionId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();

        given(ledgerRepository.save(any(LedgerEntry.class))).willAnswer(inv -> inv.getArgument(0));

        LedgerEntry entry = ledgerService.recordEntry(
            transactionId, accountId, "BUY_RESERVATION", new BigDecimal("100.0000"),
            new BigDecimal("1000.0000"), new BigDecimal("900.0000"), "ORDER", UUID.randomUUID(), "Test reservation"
        );

        assertThat(entry.getAmount()).isEqualByComparingTo("100.0000");
        assertThat(entry.getBalanceBefore()).isEqualByComparingTo("1000.0000");
        assertThat(entry.getBalanceAfter()).isEqualByComparingTo("900.0000");
        assertThat(entry.getEntryType()).isEqualTo("BUY_RESERVATION");
    }
}
