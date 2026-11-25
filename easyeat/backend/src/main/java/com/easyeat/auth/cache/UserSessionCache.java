package com.easyeat.auth.cache;

import com.easyeat.auth.domain.User;
import com.easyeat.auth.repository.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Bounded Caffeine cache for authenticated user session state (FR-005, D-05, D-06).
 * Holds up to 2000 users with a 5-minute write TTL, never caching Optional.empty(),
 * and evicts synchronously on mutations reinforced by a JPA entity listener.
 */
@Component
public class UserSessionCache {

    public static final long MAX_SIZE = 2000L;
    public static final long TTL_MINUTES = 5L;

    private static volatile UserSessionCache activeInstance;

    private final UserRepository userRepository;
    private final Cache<UUID, User> cache;

    public UserSessionCache(UserRepository userRepository) {
        this.userRepository = userRepository;
        this.cache = Caffeine.newBuilder()
                .maximumSize(MAX_SIZE)
                .expireAfterWrite(Duration.ofMinutes(TTL_MINUTES))
                .build();
        activeInstance = this;
    }

    public Optional<User> getById(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        User cached = cache.getIfPresent(userId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<User> loaded = userRepository.findById(userId);
        loaded.ifPresent(user -> cache.put(userId, user));
        return loaded;
    }

    public void evict(UUID userId) {
        if (userId != null) {
            cache.invalidate(userId);
        }
    }

    public void evictAll() {
        cache.invalidateAll();
    }

    /**
     * JPA entity listener safety net ensuring direct repository writes evict cached sessions.
     */
    public static class UserEntityListener {

        @PostPersist
        @PostUpdate
        @PostRemove
        public void onUserChange(User user) {
            UserSessionCache instance = activeInstance;
            if (instance != null && user != null) {
                instance.evict(user.getId());
            }
        }
    }
}
