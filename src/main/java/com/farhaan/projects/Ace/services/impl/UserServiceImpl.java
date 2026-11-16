package com.farhaan.projects.Ace.services.impl;

import com.farhaan.projects.Ace.Dto.auth.UserProfileResponse;
import com.farhaan.projects.Ace.error.ResourceNotFoundException;
import com.farhaan.projects.Ace.mapper.UserMapper;
import com.farhaan.projects.Ace.repositories.UserRepository;
import com.farhaan.projects.Ace.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Override
    public UserProfileResponse getProfile(Long userId) {
        return userRepository.findById(userId)
                .filter(u -> u.getDeletedAt() == null)
                .map(userMapper::toUserProfileResponse)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId.toString()));
    }
}
