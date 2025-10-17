package com.caracore.hub_town.api;

import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class HealthResourceTest {
    @AfterEach
    void clearMdc() {
        ThreadContext.clearAll();
    }

    @Test
    @DisplayName("readiness deve retornar UP quando banco responde")
    void readinessUp() {
        ThreadContext.put("trace_id", "test-trace");
        Supplier<Boolean> dbSupplier = () -> true;
        HealthResource resource = new HealthResource(dbSupplier);

        Response response = resource.readiness();

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        Object entity = response.getEntity();
        assertThat(entity).isInstanceOf(Map.class);
        Map<?, ?> payload = (Map<?, ?>) entity;
        assertThat(payload.get("status")).isEqualTo("UP");
        assertThat(payload.get("trace_id")).isEqualTo("test-trace");
        assertThat(payload.get("checks")).isInstanceOf(List.class);
        List<?> checks = (List<?>) payload.get("checks");
        assertThat(checks).hasSize(1);
        Object first = checks.get(0);
        assertThat(first).isInstanceOf(Map.class);
        Map<?, ?> db = (Map<?, ?>) first;
        assertThat(db.get("status")).isEqualTo("UP");
    }

    @Test
    @DisplayName("readiness deve retornar DOWN e 503 quando banco falha")
    void readinessDown() {
        AtomicInteger attempts = new AtomicInteger();
        Supplier<Boolean> dbSupplier = () -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("timeout");
        };
        HealthResource resource = new HealthResource(dbSupplier);

        Response response = resource.readiness();

        assertThat(response.getStatus()).isEqualTo(Response.Status.SERVICE_UNAVAILABLE.getStatusCode());
        Map<?, ?> payload = (Map<?, ?>) response.getEntity();
        assertThat(payload.get("status")).isEqualTo("DOWN");
        List<?> checks = (List<?>) payload.get("checks");
        Map<?, ?> db = (Map<?, ?>) checks.get(0);
        assertThat(db.get("status")).isEqualTo("DOWN");
        assertThat(db.get("detail")).isEqualTo("timeout");
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("live sempre retorna UP")
    void live() {
        ThreadContext.put("trace_id", "live-trace");
        HealthResource resource = new HealthResource(() -> true);

        Response response = resource.live();

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        Map<?, ?> payload = (Map<?, ?>) response.getEntity();
        assertThat(payload.get("status")).isEqualTo("UP");
        assertThat(payload.get("trace_id")).isEqualTo("live-trace");
    }
}
