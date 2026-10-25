package com.easyeat.profile.service;

import com.easyeat.auth.domain.User;
import com.easyeat.profile.dto.PersonalDataRequest;
import com.easyeat.profile.dto.ProfileResponse;
import com.easyeat.profile.repository.ProfileUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final ProfileUserRepository profileUserRepository;

    public ProfileService(ProfileUserRepository profileUserRepository) {
        this.profileUserRepository = profileUserRepository;
    }

    @Transactional(readOnly = true)
    public ProfileResponse getProfile(User user) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Transactional
    public ProfileResponse updatePersonalData(User user, PersonalDataRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
