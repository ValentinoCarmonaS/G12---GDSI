package com.easyeat.order.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.repository.UserRepository;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.dto.CompleteDeliveryResponse;
import com.easyeat.order.dto.CounterReadyOrderResponse;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.service.CounterOrderService;
import com.easyeat.profile.repository.UserPhotoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/counter")
public class CounterOrderController {

    private static final ZoneId BUENOS_AIRES_ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final KdsTicketRepository kdsTicketRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final UserPhotoRepository userPhotoRepository;
    private final Clock clock;
    private final CounterOrderService counterOrderService;

    public CounterOrderController(
            KdsTicketRepository kdsTicketRepository,
            OrderRepository orderRepository,
            UserRepository userRepository,
            UserPhotoRepository userPhotoRepository,
            Clock clock
    ) {
        this(kdsTicketRepository, orderRepository, userRepository, userPhotoRepository, clock, null);
    }

    @Autowired
    public CounterOrderController(
            KdsTicketRepository kdsTicketRepository,
            OrderRepository orderRepository,
            @Autowired(required = false) UserRepository userRepository,
            @Autowired(required = false) UserPhotoRepository userPhotoRepository,
            @Autowired(required = false) Clock clock,
            CounterOrderService counterOrderService
    ) {
        this.kdsTicketRepository = kdsTicketRepository;
        this.orderRepository = orderRepository;
        this.userRepository = userRepository;
        this.userPhotoRepository = userPhotoRepository;
        this.clock = (clock != null) ? clock : Clock.system(BUENOS_AIRES_ZONE);
        this.counterOrderService = counterOrderService;
    }

    @GetMapping("/orders")
    @PreAuthorize("hasAnyRole('ENCARGADO', 'ADMINISTRADOR')")
    @Transactional(readOnly = true)
    public ResponseEntity<List<CounterReadyOrderResponse>> getCounterReadyOrders(
            @RequestParam(required = false, defaultValue = "READY_FOR_PICKUP") String status,
            @RequestParam(required = false) UUID establishmentId,
            @AuthenticationPrincipal User user
    ) {
        UUID effectiveEstablishmentId = resolveEstablishmentId(user, establishmentId);
        LocalDate today = LocalDate.now(clock.withZone(BUENOS_AIRES_ZONE));

        List<KdsTicket> readyTickets = kdsTicketRepository.findCounterReadyTickets(
                effectiveEstablishmentId,
                today
        );

        List<CounterReadyOrderResponse> responses = readyTickets.stream()
                .filter(ticket -> ticket.getStatus() == KdsTicketStatus.READY_FOR_PICKUP)
                .filter(ticket -> ticket.getPickupDate() == null || ticket.getPickupDate().equals(today))
                .sorted(
                        Comparator.comparing(
                                        (KdsTicket t) -> t.getReadyAt() != null ? t.getReadyAt() : Instant.EPOCH
                                )
                                .thenComparing(
                                        t -> t.getTicketNumber() != null ? t.getTicketNumber() : Integer.MAX_VALUE
                                )
                )
                .map(this::mapTicketToCounterReadyOrderResponse)
                .toList();

        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(responses);
    }

    /**
     * Confirms the physical delivery of a ready order (spec 022, US2, contract 01): 200 on the first and on
     * idempotent calls, 409 when not ready, 404 when unknown, 403 for another establishment's staff.
     */
    @PostMapping("/orders/{orderId}/complete-delivery")
    @PreAuthorize("hasAnyRole('ENCARGADO', 'ADMINISTRADOR')")
    public ResponseEntity<CompleteDeliveryResponse> completeDelivery(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal User user
    ) {
        return ResponseEntity.ok(counterOrderService.completeDelivery(orderId, resolveAuthenticatedUser(user)));
    }

    private UUID resolveEstablishmentId(User user, UUID establishmentIdParam) {
        if (establishmentIdParam != null) {
            return establishmentIdParam;
        }
        User effectiveUser = resolveAuthenticatedUser(user);
        if (effectiveUser != null && effectiveUser.getTenantId() != null) {
            return effectiveUser.getTenantId();
        }
        throw new IllegalArgumentException("No establishment identified for current user");
    }

    private User resolveAuthenticatedUser(User principalUser) {
        if (principalUser != null) {
            return principalUser;
        }
        Authentication contextAuth = SecurityContextHolder.getContext().getAuthentication();
        if (contextAuth != null && contextAuth.getPrincipal() instanceof User u) {
            return u;
        }
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            var principal = attrs.getRequest().getUserPrincipal();
            if (principal instanceof Authentication auth && auth.getPrincipal() instanceof User u) {
                return u;
            }
            if (principal instanceof User u) {
                return u;
            }
        }
        return null;
    }

    private CounterReadyOrderResponse mapTicketToCounterReadyOrderResponse(KdsTicket ticket) {
        Optional<Order> orderOpt = (orderRepository != null && ticket.getOrderId() != null)
                ? orderRepository.findById(ticket.getOrderId())
                : Optional.empty();

        Order order = orderOpt.orElse(null);

        UUID customerId = (order != null) ? order.getUserId() : null;
        String customerFullName = "Comensal";
        String photoUrl = null;

        if (customerId != null) {
            if (userRepository != null) {
                Optional<User> customerOpt = userRepository.findById(customerId);
                if (customerOpt.isPresent()) {
                    User customer = customerOpt.get();
                    if (customer.getFullName() != null && !customer.getFullName().isBlank()) {
                        customerFullName = customer.getFullName();
                    } else if (customer.getFirstName() != null && !customer.getFirstName().isBlank()) {
                        String lastName = customer.getLastName() != null ? " " + customer.getLastName() : "";
                        customerFullName = (customer.getFirstName() + lastName).trim();
                    } else if (customer.getEmail() != null) {
                        customerFullName = customer.getEmail();
                    }
                }
            }

            if (userPhotoRepository != null && userPhotoRepository.existsByUserId(customerId)) {
                photoUrl = "/api/v1/profile/users/" + customerId + "/avatar";
            }
        }

        List<CounterReadyOrderResponse.CounterOrderItemDto> itemDtos = (order != null && order.getItems() != null)
                ? order.getItems().stream()
                        .map(item -> new CounterReadyOrderResponse.CounterOrderItemDto(
                                item.getQuantity(),
                                item.getDishName()
                        ))
                        .toList()
                : List.of();

        Instant readyAt = ticket.getReadyAt() != null
                ? ticket.getReadyAt()
                : (order != null ? order.getReadyAt() : null);

        String securityCode = resolveStoredSecurityCode(order);

        return new CounterReadyOrderResponse(
                ticket.getId(),
                ticket.getOrderId(),
                ticket.getTicketNumber(),
                new CounterReadyOrderResponse.TimeSlotDto(
                        ticket.getTimeSlotStart(),
                        ticket.getTimeSlotEnd()
                ),
                readyAt,
                new CounterReadyOrderResponse.CustomerSummaryDto(
                        customerId,
                        customerFullName,
                        photoUrl
                ),
                securityCode,
                itemDtos
        );
    }

    /**
     * The counter shows exactly the PIN the diner received, or nothing: a missing or malformed code yields
     * {@code null}, never a substitute (spec 022 edge case 10, contract 02 invariant 3).
     */
    private String resolveStoredSecurityCode(Order order) {
        if (order == null || order.getSecurityCode() == null) {
            return null;
        }
        String code = order.getSecurityCode().trim();
        return code.matches("^[1-9][0-9]{3}$") ? code : null;
    }
}
