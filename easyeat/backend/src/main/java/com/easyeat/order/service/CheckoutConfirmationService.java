package com.easyeat.order.service;

import com.easyeat.order.dto.PaymentConfirmationRequest;
import com.easyeat.order.dto.PaymentConfirmationResponse;
import com.easyeat.order.dto.PendingPaymentRequest;
import com.easyeat.order.dto.PendingPaymentResponse;
import com.easyeat.order.dto.ZeroAmountOrderRequest;
import com.easyeat.order.dto.ZeroAmountOrderResponse;

import java.util.UUID;

public interface CheckoutConfirmationService {

    PaymentConfirmationResponse confirmPayment(UUID dinerId, PaymentConfirmationRequest request);

    PendingPaymentResponse registerPendingPayment(UUID dinerId, PendingPaymentRequest request);

    ZeroAmountOrderResponse confirmZeroAmountOrder(UUID dinerId, ZeroAmountOrderRequest request);
}
