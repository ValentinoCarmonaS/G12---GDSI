package com.easyeat.dish.config;

import com.easyeat.dish.service.RetailPricingCalculator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Exposes the pricing calculator to the container without annotating it.
 *
 * <p>{@link RetailPricingCalculator} carries no Spring annotation on purpose: T010 requires it to
 * be free of Spring and of request types so FR-007 and FR-008 can be exhausted by pure unit tests
 * rather than inferred through a web slice. Marking it {@code @Component} would contradict that
 * design statement, so the bean is declared here instead — the same shape {@code ClockConfig}
 * already uses for {@code Clock}.
 *
 * <p>It is a singleton and safe to share: the calculator holds no state, and every method takes
 * its inputs and returns a new {@code BigDecimal}.
 */
@Configuration
public class DishPricingConfig {

    @Bean
    public RetailPricingCalculator retailPricingCalculator() {
        return new RetailPricingCalculator();
    }
}
