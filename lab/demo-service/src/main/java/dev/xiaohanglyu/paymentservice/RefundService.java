package dev.xiaohanglyu.paymentservice;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import dev.xiaohanglyu.paymentservice.LabProperties.Fault;

/**
 * Refunds part or all of a captured payment. A request above the refundable amount is
 * rejected; load generation sends a few of those on purpose, so rejections are part of the
 * normal baseline and not a signal by themselves.
 */
@Service
class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final TransactionTemplate transactions;
    private final LabProperties lab;

    RefundService(JdbcTemplate jdbc, DataSource dataSource, TransactionTemplate transactions,
            LabProperties lab) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
        this.transactions = transactions;
        this.lab = lab;
    }

    Outcome refund(long paymentId, long amountCents) throws SQLException {
        return lab.has(Fault.CONNECTION_LEAK)
                ? refundWithRowLock(paymentId, amountCents)
                : transactions.execute(status -> refundTransactional(paymentId, amountCents));
    }

    private Outcome refundTransactional(long paymentId, long amountCents) {
        Long refundable = jdbc.query(
                "SELECT amount_cents - refunded_cents FROM payments WHERE id = ? FOR UPDATE",
                rs -> rs.next() ? rs.getLong(1) : null, paymentId);
        if (refundable == null) {
            return Outcome.NOT_FOUND;
        }
        if (amountCents > refundable) {
            reject(paymentId, amountCents, refundable);
            return Outcome.REJECTED;
        }
        jdbc.update("UPDATE payments SET refunded_cents = refunded_cents + ?, status = "
                + "CASE WHEN refunded_cents + ? = amount_cents THEN 'REFUNDED' "
                + "ELSE 'PARTIALLY_REFUNDED' END WHERE id = ?",
                amountCents, amountCents, paymentId);
        return Outcome.REFUNDED;
    }

    /**
     * The CONNECTION_LEAK release: "lock the payment row to prevent double refunds",
     * written against a raw connection. The early returns skip close(), so every rejected
     * or unknown refund keeps a pooled connection, with an open transaction and a row lock,
     * forever. Successful refunds are fine, which is why it passed review.
     */
    Outcome refundWithRowLock(long paymentId, long amountCents) throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(false);
        long refundable;
        try (PreparedStatement select = connection.prepareStatement(
                "SELECT amount_cents - refunded_cents FROM payments WHERE id = ? FOR UPDATE")) {
            select.setLong(1, paymentId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Outcome.NOT_FOUND;
                }
                refundable = rs.getLong(1);
            }
        }
        if (amountCents > refundable) {
            reject(paymentId, amountCents, refundable);
            return Outcome.REJECTED;
        }
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE payments SET refunded_cents = refunded_cents + ?, status = "
                        + "CASE WHEN refunded_cents + ? = amount_cents THEN 'REFUNDED' "
                        + "ELSE 'PARTIALLY_REFUNDED' END WHERE id = ?")) {
            update.setLong(1, amountCents);
            update.setLong(2, amountCents);
            update.setLong(3, paymentId);
            update.executeUpdate();
        }
        connection.commit();
        connection.close();
        return Outcome.REFUNDED;
    }

    private static void reject(long paymentId, long amountCents, long refundable) {
        log.warn("Refund rejected for payment {}: requested {} exceeds refundable {}",
                paymentId, amountCents, refundable);
    }

    enum Outcome { REFUNDED, REJECTED, NOT_FOUND }
}
