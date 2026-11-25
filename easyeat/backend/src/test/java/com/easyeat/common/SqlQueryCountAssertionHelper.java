package com.easyeat.common;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Reusable test helper for asserting SQL prepared statement cardinality
 * via Hibernate {@link SessionFactory#getStatistics()}.
 *
 * <p>Requires {@code hibernate.generate_statistics=true} (for example via
 * {@code @TestPropertySource}) on the test context; fails fast otherwise
 * so assertions never pass silently with zero counts.
 */
public final class SqlQueryCountAssertionHelper {

    private final Statistics statistics;

    private SqlQueryCountAssertionHelper(Statistics statistics) {
        this.statistics = Objects.requireNonNull(statistics, "statistics must not be null");
        if (!statistics.isStatisticsEnabled()) {
            throw new IllegalStateException(
                    "Hibernate statistics are disabled; enable spring.jpa.properties.hibernate.generate_statistics=true");
        }
    }

    /**
     * Builds a helper by unwrapping the Hibernate {@link SessionFactory} from a JPA {@link EntityManagerFactory}.
     */
    public static SqlQueryCountAssertionHelper from(EntityManagerFactory entityManagerFactory) {
        Objects.requireNonNull(entityManagerFactory, "entityManagerFactory must not be null");
        return from(entityManagerFactory.unwrap(SessionFactory.class));
    }

    /**
     * Builds a helper from a Hibernate {@link SessionFactory}.
     */
    public static SqlQueryCountAssertionHelper from(SessionFactory sessionFactory) {
        Objects.requireNonNull(sessionFactory, "sessionFactory must not be null");
        return new SqlQueryCountAssertionHelper(sessionFactory.getStatistics());
    }

    /**
     * Resets all accumulated Hibernate statistics counters.
     */
    public void clear() {
        statistics.clear();
    }

    /**
     * Returns the number of SQL prepared statements recorded since the last {@link #clear()}.
     */
    public long getPrepareStatementCount() {
        return statistics.getPrepareStatementCount();
    }

    /**
     * Executes {@code action} after clearing statistics and asserts that prepared statements
     * do not exceed {@code maxExpected}.
     */
    public void assertMaxQueries(long maxExpected, Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        assertMaxQueries(maxExpected, () -> {
            action.run();
            return null;
        });
    }

    /**
     * Executes {@code action} after clearing statistics, asserts that prepared statements
     * do not exceed {@code maxExpected}, and returns the action's result.
     */
    public <T> T assertMaxQueries(long maxExpected, Supplier<T> action) {
        Objects.requireNonNull(action, "action must not be null");
        clear();
        T result = action.get();
        long actual = getPrepareStatementCount();
        if (actual > maxExpected) {
            throw new AssertionError(
                    String.format("Expected at most %d SQL statements, but executed %d", maxExpected, actual));
        }
        return result;
    }

    /**
     * Executes {@code action} after clearing statistics and asserts that prepared statements
     * equal {@code expected} exactly.
     */
    public void assertExactQueries(long expected, Runnable action) {
        Objects.requireNonNull(action, "action must not be null");
        clear();
        action.run();
        long actual = getPrepareStatementCount();
        if (actual != expected) {
            throw new AssertionError(
                    String.format("Expected exactly %d SQL statements, but executed %d", expected, actual));
        }
    }
}
