package dev.xiaohanglyu.paymentservice;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import dev.xiaohanglyu.paymentservice.LabProperties.Fault;

@Repository
class PaymentRepository {

    private static final String COLUMNS =
            "id, customer_ref, merchant_ref, amount_cents, refunded_cents, status, created_at";

    // Served by payments_customer_created; returns in about a millisecond.
    private static final String HISTORY = """
            SELECT %s FROM payments
            WHERE customer_ref = ?
            ORDER BY created_at DESC LIMIT 20""".formatted(COLUMNS);

    // The SLOW_QUERY release: lower() on the column means the index no longer applies, so
    // every call scans the whole table. Correct results, nothing in the logs, just slow.
    private static final String HISTORY_CASE_INSENSITIVE = """
            SELECT %s FROM payments
            WHERE lower(customer_ref) = lower(?)
            ORDER BY created_at DESC LIMIT 20""".formatted(COLUMNS);

    private final JdbcTemplate jdbc;
    private final LabProperties lab;

    PaymentRepository(JdbcTemplate jdbc, LabProperties lab) {
        this.jdbc = jdbc;
        this.lab = lab;
    }

    long create(String customerRef, String merchantRef, long amountCents) {
        Long id = jdbc.queryForObject("""
                INSERT INTO payments (customer_ref, merchant_ref, amount_cents, status)
                VALUES (?, ?, ?, 'CAPTURED') RETURNING id""",
                Long.class, customerRef, merchantRef, amountCents);
        if (id == null) {
            throw new IllegalStateException("INSERT into payments returned no id");
        }
        return id;
    }

    List<Payment> history(String customerRef) {
        String sql = lab.has(Fault.SLOW_QUERY) ? HISTORY_CASE_INSENSITIVE : HISTORY;
        return jdbc.query(sql, PaymentRepository::map, customerRef);
    }

    Optional<Payment> find(long id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM payments WHERE id = ?",
                PaymentRepository::map, id).stream().findFirst();
    }

    static Payment map(ResultSet rs, int row) throws SQLException {
        return new Payment(
                rs.getLong("id"),
                rs.getString("customer_ref"),
                rs.getString("merchant_ref"),
                rs.getLong("amount_cents"),
                rs.getLong("refunded_cents"),
                rs.getString("status"),
                rs.getObject("created_at", java.time.OffsetDateTime.class));
    }
}
