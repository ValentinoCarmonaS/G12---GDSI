package com.easyeat.order.repository;

import com.easyeat.catalog.domain.Establishment;
import com.easyeat.order.domain.FulfillmentMode;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@DisplayName("KdsTicketRepository Contract and Counter Ready Queue Query Verification Test (T006)")
class KdsTicketRepositoryCounterReadyTest {

    @Test
    @DisplayName("Should be an interface annotated with @Repository and extend JpaRepository<KdsTicket, UUID>")
    void shouldBeProperJpaRepository() {
        Class<?> clazz = KdsTicketRepository.class;
        assertThat(clazz.isInterface()).isTrue();
        assertThat(clazz.isAnnotationPresent(Repository.class)).isTrue();
        assertThat(JpaRepository.class.isAssignableFrom(clazz)).isTrue();
    }

    @Test
    @DisplayName("findByIdForUpdate declares @Lock(LockModeType.PESSIMISTIC_WRITE) and JPQL query by id")
    void findByIdForUpdate_declaresPessimisticWriteLockAndQuery() throws NoSuchMethodException {
        Method method = KdsTicketRepository.class.getMethod("findByIdForUpdate", UUID.class);
        assertThat(method.getReturnType()).isEqualTo(Optional.class);

        Lock lockAnnotation = method.getAnnotation(Lock.class);
        assertThat(lockAnnotation).isNotNull();
        assertThat(lockAnnotation.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);

        Query queryAnnotation = method.getAnnotation(Query.class);
        assertThat(queryAnnotation).isNotNull();
        assertThat(queryAnnotation.value()).contains("SELECT k FROM KdsTicket k WHERE k.id = :id");

        Parameter idParam = method.getParameters()[0];
        Param paramAnnotation = idParam.getAnnotation(Param.class);
        assertThat(paramAnnotation).isNotNull();
        assertThat(paramAnnotation.value()).isEqualTo("id");
    }

    @Test
    @DisplayName("findCounterReadyTickets declares JPQL query filtering by establishmentId, READY_FOR_PICKUP and today, ordered by readyAt ASC, ticketNumber ASC")
    void findCounterReadyTickets_declaresDeterministicQueryAndParameters() throws NoSuchMethodException {
        Method method = KdsTicketRepository.class.getMethod("findCounterReadyTickets", UUID.class, LocalDate.class);
        assertThat(method.getReturnType()).isEqualTo(List.class);

        Query queryAnnotation = method.getAnnotation(Query.class);
        assertThat(queryAnnotation).isNotNull();
        String jpql = queryAnnotation.value();
        assertThat(jpql)
                .contains("SELECT k FROM KdsTicket k")
                .contains("k.establishment.id = :establishmentId")
                .contains("k.status = com.easyeat.order.domain.KdsTicketStatus.READY_FOR_PICKUP")
                .contains("k.pickupDate = :today")
                .contains("ORDER BY k.readyAt ASC, k.ticketNumber ASC");

        Parameter[] parameters = method.getParameters();
        assertThat(parameters).hasSize(2);
        assertThat(parameters[0].getAnnotation(Param.class).value()).isEqualTo("establishmentId");
        assertThat(parameters[1].getAnnotation(Param.class).value()).isEqualTo("today");
    }

    @Test
    @DisplayName("Should allow mock invocations for findByIdForUpdate and findCounterReadyTickets")
    void shouldBeInvocableViaMock() {
        KdsTicketRepository mockRepo = Mockito.mock(KdsTicketRepository.class);
        UUID establishmentId = UUID.randomUUID();
        UUID ticketId1 = UUID.randomUUID();
        UUID ticketId2 = UUID.randomUUID();
        LocalDate today = LocalDate.of(2026, 11, 15);

        Establishment establishment = new Establishment();
        establishment.setId(establishmentId);

        KdsTicket ticket1 = new KdsTicket(
                ticketId1,
                UUID.randomUUID(),
                establishment,
                104,
                KdsTicketStatus.READY_FOR_PICKUP,
                FulfillmentMode.IMMEDIATE,
                today,
                LocalTime.of(13, 0),
                LocalTime.of(13, 15),
                Instant.parse("2026-11-15T19:20:00Z")
        );
        ticket1.setReadyAt(Instant.parse("2026-11-15T19:30:00Z"));

        KdsTicket ticket2 = new KdsTicket(
                ticketId2,
                UUID.randomUUID(),
                establishment,
                105,
                KdsTicketStatus.READY_FOR_PICKUP,
                FulfillmentMode.IMMEDIATE,
                today,
                LocalTime.of(13, 15),
                LocalTime.of(13, 30),
                Instant.parse("2026-11-15T19:22:00Z")
        );
        ticket2.setReadyAt(Instant.parse("2026-11-15T19:33:45Z"));

        when(mockRepo.findByIdForUpdate(ticketId1)).thenReturn(Optional.of(ticket1));
        when(mockRepo.findCounterReadyTickets(establishmentId, today)).thenReturn(List.of(ticket1, ticket2));

        assertThat(mockRepo.findByIdForUpdate(ticketId1)).contains(ticket1);
        assertThat(mockRepo.findCounterReadyTickets(establishmentId, today))
                .containsExactly(ticket1, ticket2);

        verify(mockRepo, times(1)).findByIdForUpdate(ticketId1);
        verify(mockRepo, times(1)).findCounterReadyTickets(establishmentId, today);
    }
}
