package com.easyeat.common;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SqlQueryCountAssertionHelperTest {

    @Mock
    private EntityManagerFactory entityManagerFactory;

    @Mock
    private SessionFactory sessionFactory;

    @Mock
    private Statistics statistics;

    @Test
    void fromEntityManagerFactory_whenStatisticsDisabled_throwsIllegalStateException() {
        when(entityManagerFactory.unwrap(SessionFactory.class)).thenReturn(sessionFactory);
        when(sessionFactory.getStatistics()).thenReturn(statistics);
        when(statistics.isStatisticsEnabled()).thenReturn(false);

        assertThatThrownBy(() -> SqlQueryCountAssertionHelper.from(entityManagerFactory))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("hibernate.generate_statistics");
    }

    @Test
    void clearAndGetPrepareStatementCount_delegateToHibernateStatistics() {
        when(sessionFactory.getStatistics()).thenReturn(statistics);
        when(statistics.isStatisticsEnabled()).thenReturn(true);
        when(statistics.getPrepareStatementCount()).thenReturn(4L);

        SqlQueryCountAssertionHelper helper = SqlQueryCountAssertionHelper.from(sessionFactory);
        helper.clear();
        long count = helper.getPrepareStatementCount();

        verify(statistics).clear();
        assertThat(count).isEqualTo(4L);
    }

    @Test
    void assertMaxQueries_whenWithinLimit_clearsBeforeActionAndPasses() {
        when(entityManagerFactory.unwrap(SessionFactory.class)).thenReturn(sessionFactory);
        when(sessionFactory.getStatistics()).thenReturn(statistics);
        when(statistics.isStatisticsEnabled()).thenReturn(true);
        when(statistics.getPrepareStatementCount()).thenReturn(6L);

        SqlQueryCountAssertionHelper helper = SqlQueryCountAssertionHelper.from(entityManagerFactory);
        AtomicBoolean executed = new AtomicBoolean(false);

        helper.assertMaxQueries(6L, () -> executed.set(true));

        assertThat(executed).isTrue();
        InOrder order = inOrder(statistics);
        order.verify(statistics).clear();
        order.verify(statistics).getPrepareStatementCount();
    }

    @Test
    void assertMaxQueries_whenExceedsLimit_throwsAssertionErrorWithCounts() {
        when(sessionFactory.getStatistics()).thenReturn(statistics);
        when(statistics.isStatisticsEnabled()).thenReturn(true);
        when(statistics.getPrepareStatementCount()).thenReturn(7L);

        SqlQueryCountAssertionHelper helper = SqlQueryCountAssertionHelper.from(sessionFactory);

        assertThatThrownBy(() -> helper.assertMaxQueries(6L, () -> {}))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("6")
                .hasMessageContaining("7");
    }

    @Test
    void assertMaxQueriesSupplier_whenWithinLimit_returnsActionResult() {
        when(sessionFactory.getStatistics()).thenReturn(statistics);
        when(statistics.isStatisticsEnabled()).thenReturn(true);
        when(statistics.getPrepareStatementCount()).thenReturn(3L);

        SqlQueryCountAssertionHelper helper = SqlQueryCountAssertionHelper.from(sessionFactory);

        String result = helper.assertMaxQueries(4L, () -> "catalog-ok");

        verify(statistics).clear();
        assertThat(result).isEqualTo("catalog-ok");
    }

    @Test
    void assertExactQueries_whenMismatch_throwsAssertionError() {
        when(sessionFactory.getStatistics()).thenReturn(statistics);
        when(statistics.isStatisticsEnabled()).thenReturn(true);
        when(statistics.getPrepareStatementCount()).thenReturn(2L);

        SqlQueryCountAssertionHelper helper = SqlQueryCountAssertionHelper.from(sessionFactory);

        assertThatThrownBy(() -> helper.assertExactQueries(1L, () -> {}))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("1")
                .hasMessageContaining("2");
    }
}
