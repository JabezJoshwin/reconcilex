package com.reconcilex.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.reconcilex.api.*;
import com.reconcilex.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.sql.Timestamp;
import java.util.*;

@Repository
public class RunStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    public RunStore(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }
    public void save(RunResponse run, List<Finding> findings, List<TransactionRow> internal, List<TransactionRow> processor) {
        try {
            jdbc.update("INSERT INTO reconciliation_runs VALUES (?, ?, ?, ?, ?, ?)", run.id(), Timestamp.from(run.createdAt()), run.status(), run.summary().internalRows(), run.summary().processorRows(), mapper.writeValueAsString(run.summary()));
        } catch (JsonProcessingException e) { throw new IllegalStateException("Cannot encode summary", e); }
        jdbc.batchUpdate("INSERT INTO reconciliation_results VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", findings, 500,
            new org.springframework.jdbc.core.ParameterizedPreparedStatementSetter<Finding>() {
                private int ordinal;
                public void setValues(java.sql.PreparedStatement ps, Finding f) throws java.sql.SQLException {
                    ps.setObject(1, run.id()); ps.setString(2, f.transactionId()); ps.setString(3, f.classification().name()); ps.setInt(4, ordinal++);
                    ps.setObject(5, f.internalAmountMinor()); ps.setString(6, f.internalCurrency()); ps.setObject(7, f.processorAmountMinor()); ps.setString(8, f.processorCurrency());
                    ps.setInt(9, f.internalOccurrences()); ps.setInt(10, f.processorOccurrences());
                }
            });
        saveSources(run.id(), "INTERNAL", internal); saveSources(run.id(), "PROCESSOR", processor);
    }
    private void saveSources(UUID id, String source, List<TransactionRow> rows) {
        jdbc.batchUpdate("INSERT INTO reconciliation_source_rows VALUES (?, ?, ?, ?, ?, ?)", rows, 500,
            new org.springframework.jdbc.core.ParameterizedPreparedStatementSetter<TransactionRow>() {
                private int row;
                public void setValues(java.sql.PreparedStatement ps, TransactionRow t) throws java.sql.SQLException {
                    ps.setObject(1,id); ps.setString(2,source); ps.setInt(3,++row); ps.setString(4,t.transactionId()); ps.setLong(5,t.amountMinor()); ps.setString(6,t.currency());
                }
            });
    }
    public Optional<RunResponse> find(UUID id) {
        return jdbc.query("SELECT * FROM reconciliation_runs WHERE id = ?", (rs,n) -> {
            try { return new RunResponse(id, rs.getTimestamp("created_at").toInstant(), rs.getString("status"), mapper.readValue(rs.getString("summary_json"), RunSummary.class)); }
            catch (JsonProcessingException e) { throw new IllegalStateException("Cannot decode summary",e); }
        },id).stream().findFirst();
    }
    public List<Finding> findings(UUID id, int offset, int limit, Classification filter) {
        String sql = "SELECT * FROM reconciliation_results WHERE run_id = ?" + (filter == null ? "" : " AND classification = ?") + " ORDER BY ordinal LIMIT ? OFFSET ?";
        Object[] args = filter == null ? new Object[]{id,limit,offset} : new Object[]{id,filter.name(),limit,offset};
        return jdbc.query(sql, (rs,n) -> new Finding(rs.getString("transaction_id"), Classification.valueOf(rs.getString("classification")),
            rs.getObject("internal_amount_minor",Long.class), rs.getString("internal_currency"), rs.getObject("processor_amount_minor",Long.class), rs.getString("processor_currency"), rs.getInt("internal_occurrences"), rs.getInt("processor_occurrences")),args);
    }
    public long count(UUID id, Classification filter) {
        return filter == null ? jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_results WHERE run_id = ?",Long.class,id)
            : jdbc.queryForObject("SELECT COUNT(*) FROM reconciliation_results WHERE run_id = ? AND classification = ?",Long.class,id,filter.name());
    }
}
