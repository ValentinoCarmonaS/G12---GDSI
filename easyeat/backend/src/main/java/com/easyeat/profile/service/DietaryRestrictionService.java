package com.easyeat.profile.service;

import com.easyeat.auth.domain.User;
import com.easyeat.profile.domain.DietaryRestriction;
import com.easyeat.profile.domain.UserDietaryRestriction;
import com.easyeat.profile.dto.DietaryPreferencesRequest;
import com.easyeat.profile.dto.DietaryRestrictionItemDto;
import com.easyeat.profile.exception.DietaryTaxonomyException;
import com.easyeat.profile.repository.DietaryRestrictionRepository;
import com.easyeat.profile.repository.UserDietaryRestrictionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DietaryRestrictionService {

    private static final Logger log = LoggerFactory.getLogger(DietaryRestrictionService.class);

    private final DietaryRestrictionRepository dietaryRestrictionRepository;
    private final UserDietaryRestrictionRepository userDietaryRestrictionRepository;

    public DietaryRestrictionService(
            DietaryRestrictionRepository dietaryRestrictionRepository,
            UserDietaryRestrictionRepository userDietaryRestrictionRepository
    ) {
        this.dietaryRestrictionRepository = dietaryRestrictionRepository;
        this.userDietaryRestrictionRepository = userDietaryRestrictionRepository;
    }

    /**
     * Returns the standardized platform dietary taxonomy ordered by category and name.
     */
    @Transactional(readOnly = true)
    public List<DietaryRestrictionItemDto> getDietaryTaxonomy() {
        return dietaryRestrictionRepository.findAllByOrderByCategoryAscNameAsc()
                .stream()
                .map(DietaryRestrictionItemDto::fromEntity)
                .toList();
    }

    /**
     * Retrieves the active dietary restrictions configured for a specific user.
     */
    @Transactional(readOnly = true)
    public List<DietaryRestrictionItemDto> getUserPreferences(UUID userId) {
        List<UserDietaryRestriction> userRestrictions = userDietaryRestrictionRepository.findByIdUserId(userId);
        if (userRestrictions.isEmpty()) {
            return List.of();
        }

        List<String> codes = userRestrictions.stream()
                .map(r -> r.getId().getRestrictionCode())
                .toList();

        return dietaryRestrictionRepository.findAllById(codes)
                .stream()
                .map(DietaryRestrictionItemDto::fromEntity)
                .toList();
    }

    /**
     * Atomically replaces the user's active dietary restrictions.
     * Enforces pre-validation against the platform taxonomy: if any code is invalid,
     * a DietaryTaxonomyException is thrown and zero state is altered.
     */
    @Transactional
    public List<DietaryRestrictionItemDto> updateUserPreferences(User user, DietaryPreferencesRequest request) {
        List<String> rawCodes = request.restrictionCodes() != null ? request.restrictionCodes() : List.of();

        List<String> cleanCodes = rawCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .map(String::toUpperCase)
                .distinct()
                .toList();

        if (cleanCodes.isEmpty()) {
            userDietaryRestrictionRepository.deleteByUserId(user.getId());
            log.info("Cleared all dietary restrictions for user ID: {}", user.getId());
            return List.of();
        }

        List<DietaryRestriction> matchedRestrictions = dietaryRestrictionRepository.findAllById(cleanCodes);
        Set<String> matchedCodes = matchedRestrictions.stream()
                .map(DietaryRestriction::getCode)
                .collect(Collectors.toSet());

        for (String code : cleanCodes) {
            if (!matchedCodes.contains(code)) {
                log.warn("Invalid dietary restriction code detected: {} for user ID: {}", code, user.getId());
                throw DietaryTaxonomyException.forInvalidCode(code);
            }
        }

        userDietaryRestrictionRepository.deleteByUserId(user.getId());

        List<UserDietaryRestriction> newPreferences = cleanCodes.stream()
                .map(code -> new UserDietaryRestriction(user.getId(), code))
                .toList();
        userDietaryRestrictionRepository.saveAll(newPreferences);

        log.info("Atomically updated dietary preferences for user ID: {} to {}", user.getId(), cleanCodes);

        return matchedRestrictions.stream()
                .map(DietaryRestrictionItemDto::fromEntity)
                .toList();
    }
}
