package com.tradex.order;

import com.tradex.common.dto.ErrorResponse;
import com.tradex.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import static org.assertj.core.api.Assertions.assertThat;

class OptimisticLockingExceptionMappingTest {

    @Test
    @DisplayName("Optimistic Locking Exception Mapping — ObjectOptimisticLockingFailureException maps to HTTP 409 CONFLICT")
    void optimisticLockingMapsToConflict() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        ObjectOptimisticLockingFailureException exception = new ObjectOptimisticLockingFailureException("Order", "123");

        ResponseEntity<ErrorResponse> response = handler.handleOptimisticLockingFailure(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("CONCURRENT_ORDER_UPDATE");
        assertThat(response.getBody().message()).isEqualTo("Order was modified concurrently. Please retry.");
    }
}
