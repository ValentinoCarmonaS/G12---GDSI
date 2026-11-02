package com.easyeat.operations.service;

import com.easyeat.operations.domain.EstablishmentPaymentMethod;
import com.easyeat.operations.domain.PaymentMethod;
import com.easyeat.operations.exception.OperatingConfigConflictException;
import com.easyeat.operations.exception.PaymentMethodUnavailableException;
import com.easyeat.operations.repository.PaymentMethodRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Visibility switches of the online payment methods (ADM-2.2.3). It stores a boolean per method
 * and nothing else: no gateway, credential or charge (A-005). A method without a row is enabled,
 * because only the establishments that existed when V12 ran were seeded; the row is created by
 * the first admin toggle. Reading a missing row as disabled would block the checkout of every new
 * establishment (EC-009).
 */
@Service
public class PaymentMethodService {

    /** A method as the admin sees it; {@code id} and {@code version} are null while it has no row. */
    public record View(UUID id, PaymentMethod paymentMethod, String displayName, boolean enabled, Long version) {
    }

    private final PaymentMethodRepository repository;

    public PaymentMethodService(PaymentMethodRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<View> getAdminView(UUID establishmentId) {
        List<EstablishmentPaymentMethod> rows = repository.findByEstablishmentId(establishmentId);
        return Arrays.stream(PaymentMethod.values())
                .map(method -> rows.stream()
                        .filter(row -> row.getPaymentMethod() == method)
                        .findFirst()
                        .map(PaymentMethodService::toView)
                        .orElseGet(() -> new View(null, method, method.displayName(), true, null)))
                .toList();
    }

    /** INV-4: only the enabled methods, evaluated on every call and never cached (INV-9). */
    @Transactional(readOnly = true)
    public List<PaymentMethod> getEnabledMethods(UUID establishmentId) {
        return getAdminView(establishmentId).stream().filter(View::enabled).map(View::paymentMethod).toList();
    }

    /** INV-9: the method must be enabled right now, at the moment the order is confirmed. */
    @Transactional(readOnly = true)
    public void validateEnabled(UUID establishmentId, PaymentMethod paymentMethod) {
        boolean enabled = repository.findByEstablishmentIdAndPaymentMethod(establishmentId, paymentMethod)
                .map(EstablishmentPaymentMethod::isEnabled)
                .orElse(true);
        if (!enabled) {
            throw new PaymentMethodUnavailableException(paymentMethod);
        }
    }

    @Transactional
    public View toggle(UUID establishmentId, PaymentMethod paymentMethod, boolean enabled, Long expectedVersion) {
        Optional<EstablishmentPaymentMethod> existing =
                repository.findByEstablishmentIdAndPaymentMethod(establishmentId, paymentMethod);
        EstablishmentPaymentMethod row;
        if (existing.isPresent()) {
            row = existing.get();
            if (!row.getVersion().equals(expectedVersion)) {
                throw new OperatingConfigConflictException();
            }
            row.setEnabled(enabled);
        } else {
            if (expectedVersion != null) {
                throw new OperatingConfigConflictException();
            }
            row = new EstablishmentPaymentMethod(establishmentId, paymentMethod, enabled);
        }
        try {
            return toView(repository.saveAndFlush(row));
        } catch (ObjectOptimisticLockingFailureException | DataIntegrityViolationException e) {
            throw new OperatingConfigConflictException();
        }
    }

    private static View toView(EstablishmentPaymentMethod row) {
        return new View(row.getId(), row.getPaymentMethod(), row.getPaymentMethod().displayName(),
                row.isEnabled(), row.getVersion());
    }
}
