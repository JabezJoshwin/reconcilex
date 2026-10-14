package com.reconcilex.auth;

import java.time.Instant;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UserStore {
    private final JdbcTemplate jdbc;
    public UserStore(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public UserIdentity provision(String issuer, String subject, String email, String name) {
        if (issuer == null || subject == null || subject.isBlank() || subject.length() > 255 ||
            email == null || email.length() > 320) throw new IllegalArgumentException("Invalid identity");
        String display = name == null || name.isBlank() ? "ReconcileX user" : name.substring(0, Math.min(name.length(),255));
        var existing = jdbc.query("SELECT id,display_name,email FROM app_users WHERE issuer=? AND subject=?",
            (rs,n) -> new UserIdentity(rs.getObject("id",UUID.class),rs.getString("display_name"),rs.getString("email")),issuer,subject);
        if (!existing.isEmpty()) return existing.getFirst();
        try {
            jdbc.update("INSERT INTO app_users (id,issuer,subject,email,display_name,created_at) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(),issuer,subject,email,display,Timestamp.from(Instant.now()));
        } catch (DuplicateKeyException race) {
            // Another first login won the unique issuer/subject insert. Read that account.
        }
        return jdbc.queryForObject("SELECT id,display_name,email FROM app_users WHERE issuer=? AND subject=?",
            (rs,n) -> new UserIdentity(rs.getObject("id",UUID.class),rs.getString("display_name"),rs.getString("email")),issuer,subject);
    }
    public UserIdentity require(UUID id) {
        return jdbc.queryForObject("SELECT id,display_name,email FROM app_users WHERE id=?",
            (rs,n) -> new UserIdentity(id,rs.getString("display_name"),rs.getString("email")),id);
    }
}
