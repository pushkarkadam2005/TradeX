package com.tradex.account;

import com.tradex.account.entity.Account;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AccountTest {

    @Test
    @DisplayName("Account Entity Initialization — Default zero balance, USD currency, and field getters")
    void accountInitialization() {
        UUID userId = UUID.randomUUID();
        Account account = new Account(userId, "USD");

        assertThat(account.getUserId()).isEqualTo(userId);
        assertThat(account.getCurrency()).isEqualTo("USD");
        assertThat(account.getAvailableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(account.getLockedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Fund Reservation & Release — Moves available balance to locked and back without losing funds")
    void fundReservationAndRelease() {
        UUID userId = UUID.randomUUID();
        Account account = new Account(userId, new BigDecimal("1000.0000"));

        // Reserve $300
        account.reserveFunds(new BigDecimal("300.0000"));
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("700.0000");
        assertThat(account.getLockedBalance()).isEqualByComparingTo("300.0000");

        // Release $100
        account.releaseFunds(new BigDecimal("100.0000"));
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("800.0000");
        assertThat(account.getLockedBalance()).isEqualByComparingTo("200.0000");
    }

    @Test
    @DisplayName("Insufficient Available Balance — Throws IllegalStateException when reserving more than available")
    void insufficientAvailableBalanceThrowsException() {
        Account account = new Account(UUID.randomUUID(), new BigDecimal("100.0000"));

        assertThatThrownBy(() -> account.reserveFunds(new BigDecimal("150.0000")))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Insufficient available balance");
    }
}
