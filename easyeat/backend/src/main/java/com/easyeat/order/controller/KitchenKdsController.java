package com.easyeat.order.controller;

import com.easyeat.auth.domain.User;
import com.easyeat.order.domain.KdsTicket;
import com.easyeat.order.domain.KdsTicketStatus;
import com.easyeat.order.domain.Order;
import com.easyeat.order.domain.OrderItem;
import com.easyeat.order.dto.KdsDashboardSummaryResponse;
import com.easyeat.order.repository.KdsTicketRepository;
import com.easyeat.order.repository.OrderRepository;
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

    public KitchenKdsController(
            KdsTicketRepository kdsTicketRepository,
            OrderRepository orderRepository
    ) {
        this.kdsTicketRepository = kdsTicketRepository;
        this.orderRepository = orderRepository;
    }

    @GetMapping("/orders")
    @PreAuthorize("hasRole('ROLE_CHEF') or hasRole('CHEF') or hasRole('ROLE_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getKitchenOrders(
            @RequestParam(required = false) KdsTicketStatus status,
            @RequestParam(required = false) UUID establishmentId,
            @PageableDefault(size = 20) Pageable pageable,
            @AuthenticationPrincipal User user
    ) {
        UUID effectiveEstablishmentId = resolveEstablishmentId(user, establishmentId);

        Page<KdsTicket> ticketsPage;
        if (status != null) {
            ticketsPage = kdsTicketRepository.findByEstablishmentIdAndStatus(effectiveEstablishmentId, status, pageable);
        } else {
            ticketsPage = kdsTicketRepository.findByEstablishmentId(effectiveEstablishmentId, pageable);
        }

        List<Map<String, Object>> content = ticketsPage.getContent().stream()
                .map(this::mapTicketToKdsResponse)
                .toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", content);
        response.put("pageable", Map.of(
                "pageNumber", ticketsPage.getNumber(),
                "pageSize", ticketsPage.getSize()
        ));
        response.put("totalElements", ticketsPage.getTotalElements());
        response.put("totalPages", ticketsPage.getTotalPages());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/dashboard/summary")
    @PreAuthorize("hasRole('ROLE_CHEF') or hasRole('CHEF') or hasRole('ROLE_ADMIN') or hasRole('ADMIN')")
    public ResponseEntity<KdsDashboardSummaryResponse> getDashboardSummary(
            @RequestParam(required = false) UUID establishmentId,
            @AuthenticationPrincipal User user
    ) {
        UUID effectiveEstablishmentId = resolveEstablishmentId(user, establishmentId);
        LocalDate today = LocalDate.now();

        long queued = kdsTicketRepository.countByEstablishmentIdAndStatus(effectiveEstablishmentId, KdsTicketStatus.QUEUED);
        long inPrep = kdsTicketRepository.countByEstablishmentIdAndStatus(effectiveEstablishmentId, KdsTicketStatus.IN_PREPARATION);
        long ready = kdsTicketRepository.countByEstablishmentIdAndStatus(effectiveEstablishmentId, KdsTicketStatus.READY_FOR_PICKUP);
        long completed = kdsTicketRepository.countByEstablishmentIdAndStatusAndPickupDate(effectiveEstablishmentId, KdsTicketStatus.COMPLETED, today);

        return ResponseEntity.ok(new KdsDashboardSummaryResponse(queued, inPrep, ready, completed));
    }

    private UUID resolveEstablishmentId(User user, UUID establishmentIdParam) {
        if (establishmentIdParam != null) {
            return establishmentIdParam;
        }
        if (user != null && user.getTenantId() != null) {
            return user.getTenantId();
        }
        throw new IllegalArgumentException("No establishment identified for current user");
    }

    private Map<String, Object> mapTicketToKdsResponse(KdsTicket ticket) {
        Map<String, Object> dto = new LinkedHashMap<>();
        dto.put("ticketId", ticket.getId());
        dto.put("ticketNumber", ticket.getTicketNumber());
        dto.put("orderId", ticket.getOrderId());
        dto.put("status", ticket.getStatus().name());
        dto.put("fulfillmentMode", ticket.getFulfillmentMode() != null ? ticket.getFulfillmentMode().name() : null);
        dto.put("pickupDate", ticket.getPickupDate());
        dto.put("timeSlotStart", ticket.getTimeSlotStart());
        dto.put("timeSlotEnd", ticket.getTimeSlotEnd());
        dto.put("queuedAt", ticket.getQueuedAt());

        Optional<Order> orderOpt = orderRepository.findById(ticket.getOrderId());
        List<Map<String, Object>> items = new ArrayList<>();
        if (orderOpt.isPresent()) {
            Order order = orderOpt.get();
            if (order.getItems() != null) {
                for (OrderItem oi : order.getItems()) {
                    items.add(Map.of(
                            "dishId", oi.getDishId(),
                            "dishName", oi.getDishName() != null ? oi.getDishName() : "Plato",
                            "quantity", oi.getQuantity()
                    ));
                }
            }
        }
        dto.put("items", items);

        return dto;
    }
}
