package com.easyeat.profile.domain;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.domain.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class UserProfileEntityTest {

    @Test
    @DisplayName("User entity should support first_name, last_name, phone, and email verification fields")
    void userProfileFields() {
        User user = new User("diner@example.com", "hash", UserRole.COMENSAL);
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(86400);

        user.setFirstName("Valentino");
        user.setLastName("Carmona");
        user.setPhone("+54 9 11 2345-6789");
        user.setPendingEmail("new.email@example.com");
        user.setEmailVerificationTokenHash("sha256hashstring1234567890abcdef1234567890abcdef1234567890abcdef");
        user.setEmailTokenExpiresAt(expires);
        user.setEmailVerificationLastSentAt(now);

        assertThat(user.getFirstName()).isEqualTo("Valentino");
        assertThat(user.getLastName()).isEqualTo("Carmona");
        assertThat(user.getPhone()).isEqualTo("+54 9 11 2345-6789");
        assertThat(user.getPendingEmail()).isEqualTo("new.email@example.com");
        assertThat(user.getEmailVerificationTokenHash()).isEqualTo("sha256hashstring1234567890abcdef1234567890abcdef1234567890abcdef");
        assertThat(user.getEmailTokenExpiresAt()).isEqualTo(expires);
        assertThat(user.getEmailVerificationLastSentAt()).isEqualTo(now);
    }
}
