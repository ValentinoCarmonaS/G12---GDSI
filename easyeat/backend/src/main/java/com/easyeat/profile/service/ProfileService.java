package com.easyeat.profile.service;

import com.easyeat.auth.domain.User;
import com.easyeat.profile.dto.PersonalDataRequest;
import com.easyeat.profile.dto.ProfileResponse;
import com.easyeat.profile.repository.ProfileUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileService.class);

    private static final String NAME_REGEX = "^[A-Za-zÁÉÍÓÚáéíóúÑñüÜ ]+$";
    private static final String PHONE_REGEX = "^\\+?[0-9 -]{8,20}$";

    private final ProfileUserRepository profileUserRepository;

    public ProfileService(ProfileUserRepository profileUserRepository) {
        this.profileUserRepository = profileUserRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(User user) {
        User target = profileUserRepository.findById(user.getId()).orElse(user);
        boolean hasPending = target.getPendingEmail() != null && !target.getPendingEmail().isBlank();

        return new ProfileResponse(
                target.getId(),
                target.getFirstName(),
                target.getLastName(),
                target.getFullName(),
                target.getPhone(),
                target.getEmail(),
                target.getPendingEmail(),
                hasPending,
                List.of()
        );
    }

    @Transactional
    public ProfileResponse updatePersonalData(User user, PersonalDataRequest request) {
        validatePersonalData(request);

        User target = profileUserRepository.findById(user.getId()).orElse(user);
        String cleanFirstName = request.firstName().trim();
        String cleanLastName = request.lastName().trim();
        String cleanPhone = (request.phone() != null && !request.phone().isBlank()) ? request.phone().trim() : null;

        target.setFirstName(cleanFirstName);
        target.setLastName(cleanLastName);
        target.setFullName(cleanFirstName + " " + cleanLastName);
        target.setPhone(cleanPhone);

        target = profileUserRepository.save(target);
        log.info("Personal data updated for user ID: {}. full_name synchronized to: {}", target.getId(), target.getFullName());

        boolean hasPending = target.getPendingEmail() != null && !target.getPendingEmail().isBlank();
        return new ProfileResponse(
                target.getId(),
                target.getFirstName(),
                target.getLastName(),
                target.getFullName(),
                target.getPhone(),
                target.getEmail(),
                target.getPendingEmail(),
                hasPending,
                List.of()
        );
    }

    private void validatePersonalData(PersonalDataRequest request) {
        if (request.firstName() == null || request.firstName().isBlank()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        if (!request.firstName().trim().matches(NAME_REGEX)) {
            throw new IllegalArgumentException("El nombre solo puede contener letras y espacios");
        }
        if (request.lastName() == null || request.lastName().isBlank()) {
            throw new IllegalArgumentException("El apellido es obligatorio");
        }
        if (!request.lastName().trim().matches(NAME_REGEX)) {
            throw new IllegalArgumentException("El apellido solo puede contener letras y espacios");
        }
        if (request.phone() != null && !request.phone().isBlank()) {
            if (!request.phone().trim().matches(PHONE_REGEX)) {
                throw new IllegalArgumentException("El formato del teléfono es inválido");
            }
        }
    }
}
