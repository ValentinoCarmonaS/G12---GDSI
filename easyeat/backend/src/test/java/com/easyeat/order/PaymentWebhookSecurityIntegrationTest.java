package com.easyeat.order;

import com.easyeat.auth.repository.UserRepository;
import com.easyeat.auth.security.JwtAuthenticationFilter;
import com.easyeat.auth.security.JwtService;
import com.easyeat.config.SecurityConfig;
import com.easyeat.order.controller.PaymentWebhookController;
import com.easyeat.order.service.PaymentGatewayResolver;
import com.easyeat.order.service.PaymentWebhookService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@WebMvcTest(controllers = PaymentWebhookController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@DisplayName("PaymentWebhookSecurityIntegrationTest (T024)")
class PaymentWebhookSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PaymentGatewayResolver paymentGatewayResolver;

    @MockBean
    private PaymentWebhookService paymentWebhookService;

    @Test
    @DisplayName("POST /api/v1/payments/mercadopago/webhook debe ser accesible sin autenticación y no retornar 401 ni 403")
    void postWebhookMercadoPago_whenAnonymous_shouldNotBeRejectedBySecurity() throws Exception {
        mockMvc.perform(post("/api/v1/payments/mercadopago/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status)
                            .withFailMessage("El webhook no debe ser rechazado por seguridad (401 o 403), pero devolvió: " + status)
                            .isNotIn(401, 403);
                });
    }

    @Test
    @DisplayName("POST /api/v1/payments/{provider}/webhook debe ser accesible sin autenticación y no retornar 401 ni 403")
    void postWebhookAnyProvider_whenAnonymous_shouldNotBeRejectedBySecurity() throws Exception {
        mockMvc.perform(post("/api/v1/payments/custom-provider/webhook")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertThat(status)
                            .withFailMessage("El webhook genérico no debe ser rechazado por seguridad (401 o 403), pero devolvió: " + status)
                            .isNotIn(401, 403);
                });
    }
}
