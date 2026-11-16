package com.easyeat.order.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderItem;
import com.easyeat.order.domain.OrderStatus;
import com.easyeat.order.dto.KdsActiveQueueTicketResponse;
import com.easyeat.order.dto.KdsDashboardSummaryResponse;
import com.easyeat.order.dto.KdsDelayIncrementResponse;
import com.easyeat.order.dto.KdsOrderItemResponse;
import com.easyeat.order.dto.KdsQueuePageResponse;
import com.easyeat.order.dto.KdsStartPreparationResponse;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
import com.easyeat.order.service.KdsQueueService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/kitchen")
public class KitchenKdsController {

    private final KdsTicketRepository kdsTicketRepository;
    private final OrderRepository orderRepository;
    private final KdsQueueService kdsQueueService;

    public KitchenKdsController(
            KdsTicketRepository kdsTicketRepository,
            OrderRepository orderRepository,
            KdsQueueService kdsQueueService
    ) {
        this.kdsTicketRepository = kdsTicketRepository;
        this.orderRepository = orderRepository;
        this.kdsQueueService = kdsQueueService;
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('ROLE_CHEF') or hasRole('CHEF')")
    public ResponseEntity<KdsQueuePageResponse> getKitchenOrders(
            @RequestParam(required = false, defaultValue = "false") boolean activeQueue,
            @RequestParam(required = false) KdsTicketStatus status,
            @RequestParam(required = false) UUID establishmentId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user,
            org.springframework.security.core.Authentication authentication
    ) {
        UUID effectiveEstablishmentId = resolveEstablishmentId(user, authentication, establishmentId);

        if (activeQueue) {
            LocalDate today = LocalDate.now();
            List<KdsTicket> activeTickets = kdsTicketRepository.findActiveQueueTickets(effectiveEstablishmentId, today);
            
            // Batch loading de órdenes para evitar N+1
            List<UUID> orderIds = activeTickets.stream().map(KdsTicket::getOrderId).distinct().toList();
            Map<UUID, Order> ordersById = new HashMap<>();
            if (!orderIds.isEmpty()) {
                orderRepository.findAllByIdWithItems(orderIds).forEach(o -> ordersById.put(o.getId(), o));
            }

            List<KdsActiveQueueTicketResponse> content = activeTickets.stream()
                    .map(ticket -> mapTicketToActiveQueueResponse(ticket, ordersById.get(ticket.getOrderId())))
                    .toList();

            Map<String, Object> pageableMap = Map.of(
                    "pageNumber", 0,
                    "pageSize", Math.max(content.size(), 100)
            );

            return ResponseEntity.ok(new KdsQueuePageResponse(
                    content,
                    pageableMap,
                    content.size(),
                    1
            ));
        }

        Page<KdsTicket> ticketsPage;
        if (status != null) {
            ticketsPage = kdsTicketRepository.findByEstablishmentIdAndStatus(effectiveEstablishmentId, status, pageable);
        } else {
            ticketsPage = kdsTicketRepository.findByEstablishmentId(effectiveEstablishmentId, pageable);
        }

        List<UUID> orderIds = ticketsPage.getContent().stream().map(KdsTicket::getOrderId).distinct().toList();
        Map<UUID, Order> ordersById = new HashMap<>();
        if (!orderIds.isEmpty()) {
            orderRepository.findAllByIdWithItems(orderIds).forEach(o -> ordersById.put(o.getId(), o));
        }

        List<KdsActiveQueueTicketResponse> content = ticketsPage.getContent().stream()
                .map(ticket -> mapTicketToActiveQueueResponse(ticket, ordersById.get(ticket.getOrderId())))
                .toList();

        Map<String, Object> pageableMap = Map.of(
                "pageNumber", ticketsPage.getNumber(),
                "pageSize", ticketsPage.getSize()
        );

        return ResponseEntity.ok(new KdsQueuePageResponse(
                content,
                pageableMap,
                ticketsPage.getTotalElements(),
                ticketsPage.getTotalPages()
        ));
    }

    @GetMapping("/dashboard/summary")
    @PreAuthorize("hasRole('ROLE_CHEF') or hasRole('CHEF') or hasRole('ROLE_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<KdsDashboardSummaryResponse> getDashboardSummary(
            @RequestParam(required = false) UUID establishmentId,
            @AuthenticationPrincipal User user,
            org.springframework.security.core.Authentication authentication
    ) {
        UUID effectiveEstablishmentId = resolveEstablishmentId(user, authentication, establishmentId);
        LocalDate today = LocalDate.now();

        long queued = kdsTicketRepository.countByEstablishmentIdAndStatus(effectiveEstablishmentId, KdsTicketStatus.QUEUED);
        long inPrep = kdsTicketRepository.countByEstablishmentIdAndStatus(effectiveEstablishmentId, KdsTicketStatus.IN_PREPARATION);
        long ready = kdsTicketRepository.countByEstablishmentIdAndStatus(effectiveEstablishmentId, KdsTicketStatus.READY_FOR_PICKUP);
        long completed = kdsTicketRepository.countByEstablishmentIdAndStatusAndPickupDate(effectiveEstablishmentId, KdsTicketStatus.COMPLETED, today);

        return ResponseEntity.ok(new KdsDashboardSummaryResponse(queued, inPrep, ready, completed));
    }

    @PostMapping("/tickets/{ticketId}/start-preparation")
    @PreAuthorize("hasRole('ROLE_CHEF') or hasRole('CHEF')")
    public ResponseEntity<KdsStartPreparationResponse> startPreparation(
            @PathVariable UUID ticketId,
            @RequestParam(required = false) UUID establishmentId,
            @AuthenticationPrincipal User user,
            org.springframework.security.core.Authentication authentication
    ) {
        UUID effectiveEstablishmentId = resolveEstablishmentId(user, authentication, establishmentId);
        KdsStartPreparationResponse response = kdsQueueService.startTicketPreparation(ticketId, effectiveEstablishmentId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/tickets/{ticketId}/add-delay")
    @PreAuthorize("hasRole('ROLE_CHEF') or hasRole('CHEF') or hasRole('ROLE_ADMINISTRADOR') or hasRole('ADMINISTRADOR') or hasRole('ROLE_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<KdsDelayIncrementResponse> addTicketDelay(
            @PathVariable UUID ticketId,
            @RequestParam(required = false) UUID establishmentId,
            @AuthenticationPrincipal User user,
            org.springframework.security.core.Authentication authentication
    ) {
        UUID effectiveEstablishmentId = resolveEstablishmentId(user, authentication, establishmentId);
        KdsDelayIncrementResponse response = kdsQueueService.addTicketDelay(ticketId, effectiveEstablishmentId);
        return ResponseEntity.ok(response);
    }

    private UUID resolveEstablishmentId(User user, org.springframework.security.core.Authentication authentication, UUID establishmentIdParam) {
        if (establishmentIdParam != null) {
            return establishmentIdParam;
        }
        if (user != null && user.getTenantId() != null) {
            return user.getTenantId();
        }
        if (authentication != null && authentication.getPrincipal() instanceof User u && u.getTenantId() != null) {
            return u.getTenantId();
        }
        throw new IllegalArgumentException("No establishment identified for current user");
    }

    private KdsActiveQueueTicketResponse mapTicketToActiveQueueResponse(KdsTicket ticket, Order order) {
        List<KdsOrderItemResponse> items = new ArrayList<>();
        if (order != null && order.getItems() != null) {
            for (OrderItem oi : order.getItems()) {
                items.add(new KdsOrderItemResponse(
                        oi.getDishId(),
                        oi.getDishName() != null ? oi.getDishName() : "Plato",
                        oi.getQuantity()
                ));
            }
        }

        return new KdsActiveQueueTicketResponse(
                ticket.getId(),
                ticket.getTicketNumber(),
                ticket.getOrderId(),
                ticket.getStatus(),
                ticket.getFulfillmentMode(),
                ticket.getPickupDate(),
                ticket.getTimeSlotStart(),
                ticket.getTimeSlotEnd(),
                ticket.getQueuedAt(),
                ticket.getStartedAt(),
                items
        );
    }
}
