package com.reconcilex.api;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class HealthController {
    private final JdbcTemplate jdbc;
    private final String revision;
    public HealthController(JdbcTemplate jdbc,@Value("${app.revision:local}") String revision) { this.jdbc=jdbc; this.revision=revision; }
    @GetMapping("/health") public Map<String,String> health() { return Map.of("status","UP","revision",revision); }
    @GetMapping("/ready") public ResponseEntity<Map<String,String>> ready() {
        try {
            // Per-statement timeout without changing the shared template's configuration.
            jdbc.query(connection -> { var statement=connection.prepareStatement("SELECT 1"); statement.setQueryTimeout(3); return statement; },rs -> { if(!rs.next()) throw new java.sql.SQLException("Unavailable"); return rs.getInt(1); });
            return ResponseEntity.ok(Map.of("status","UP"));
        } catch(org.springframework.dao.DataAccessException error) {
            return ResponseEntity.status(503).body(Map.of("status","DOWN"));
        }
    }
}
