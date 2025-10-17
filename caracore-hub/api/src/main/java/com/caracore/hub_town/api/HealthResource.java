package com.caracore.hub_town.api;

import com.caracore.hub_town.config.DatabaseConfig;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.apache.logging.log4j.ThreadContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Path("/health")
@Produces(MediaType.APPLICATION_JSON)
public class HealthResource {
    private final Supplier<Boolean> dbCheck;

    public HealthResource() {
        this(DatabaseConfig.getInstance()::testConnection);
    }

    HealthResource(Supplier<Boolean> dbCheck) {
        this.dbCheck = dbCheck;
    }

    @GET
    public Response live() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", "UP");
        payload.put("timestamp", Instant.now().toString());
        payload.put("trace_id", ThreadContext.get("trace_id"));
        return Response.ok(payload).build();
    }

    @GET
    @Path("/ready")
    public Response readiness() {
        Instant now = Instant.now();
        boolean dbUp = true;
        String detail = "ok";
        try {
            Boolean result = dbCheck.get();
            dbUp = Boolean.TRUE.equals(result);
            if (!dbUp && result != null) {
                detail = "database check returned false";
            }
        } catch (Exception ex) {
            dbUp = false;
            detail = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("status", dbUp ? "UP" : "DOWN");
        payload.put("timestamp", now.toString());
        payload.put("trace_id", ThreadContext.get("trace_id"));

        List<Map<String, Object>> checks = new ArrayList<>();
        Map<String, Object> db = new LinkedHashMap<>();
        db.put("name", "database");
        db.put("status", dbUp ? "UP" : "DOWN");
        db.put("detail", detail);
        checks.add(db);
        payload.put("checks", checks);

        return Response.status(dbUp ? Response.Status.OK : Response.Status.SERVICE_UNAVAILABLE)
                .entity(payload)
                .build();
    }
}
