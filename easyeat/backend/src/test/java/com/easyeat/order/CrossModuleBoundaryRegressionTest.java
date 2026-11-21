package com.easyeat.order;

import com.easyeat.order.controller.CounterOrderController;
import com.easyeat.order.controller.KitchenKdsController;
import com.easyeat.order.controller.OrderConfirmationTrackingController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class CrossModuleBoundaryRegressionTest {

    @Test
    @DisplayName("Spec 019 (KDS): KitchenKdsController preserves its base route and CHEF authorization")
    void kdsControllerRemainsOperationalAndProtected() {
        RequestMapping mapping = KitchenKdsController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).contains("/api/v1/kitchen");

        boolean hasChefSecuredEndpoint = Arrays.stream(KitchenKdsController.class.getDeclaredMethods())
                .anyMatch(m -> m.isAnnotationPresent(PreAuthorize.class)
                        && m.getAnnotation(PreAuthorize.class).value().contains("CHEF"));
        assertThat(hasChefSecuredEndpoint).isTrue();
    }

    @Test
    @DisplayName("Spec 020 & 022 (Mostrador + PIN de entrega): CounterOrderController preserves routes and ENCARGADO authorization")
    void counterOrderControllerRemainsOperationalAndProtected() {
        RequestMapping mapping = CounterOrderController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).contains("/api/v1/counter");

        boolean hasGetOrders = Arrays.stream(CounterOrderController.class.getDeclaredMethods())
                .anyMatch(m -> m.isAnnotationPresent(GetMapping.class)
                        && m.isAnnotationPresent(PreAuthorize.class)
                        && m.getAnnotation(PreAuthorize.class).value().contains("ENCARGADO"));
        assertThat(hasGetOrders).isTrue();

        boolean hasPinOrDeliveryEndpoint = Arrays.stream(CounterOrderController.class.getDeclaredMethods())
                .anyMatch(m -> (m.isAnnotationPresent(PostMapping.class) || m.isAnnotationPresent(PatchMapping.class))
                        && m.getName().toLowerCase().contains("deliver"));
        assertThat(hasPinOrDeliveryEndpoint).isTrue();
    }

    @Test
    @DisplayName("Spec 021 (Seguimiento): OrderConfirmationTrackingController preserves tracking route")
    void orderTrackingControllerRemainsOperationalAndProtected() {
        RequestMapping mapping = OrderConfirmationTrackingController.class.getAnnotation(RequestMapping.class);
        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).anyMatch(v -> v.contains("/orders"));

        boolean hasTrackingGet = Arrays.stream(OrderConfirmationTrackingController.class.getDeclaredMethods())
                .anyMatch(m -> m.isAnnotationPresent(GetMapping.class));
        assertThat(hasTrackingGet).isTrue();
    }
}
