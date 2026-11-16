package com.farhaan.projects.Ace.services.impl;

import com.farhaan.projects.Ace.Dto.auth.AuthResponse;
import com.farhaan.projects.Ace.Dto.auth.LoginRequest;
import com.farhaan.projects.Ace.Dto.auth.SignUpRequest;
import com.farhaan.projects.Ace.entities.User;
import com.farhaan.projects.Ace.error.BadRequestException;
import com.farhaan.projects.Ace.error.UnauthorizedException;
import com.farhaan.projects.Ace.mapper.UserMapper;
import com.farhaan.projects.Ace.repositories.UserRepository;
import com.farhaan.projects.Ace.security.JwtService;
import com.farhaan.projects.Ace.services.AuthService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AuthServiceImpl implements AuthService {

    UserRepository userRepository;
    UserMapper userMapper;
    PasswordEncoder passwordEncoder;
    JwtService jwtService;


    @Override
    public AuthResponse signup(SignUpRequest request) {
        String username = request.username().trim().toLowerCase();   // e-mails are case-insensitive
        userRepository.findByUsername(username).ifPresent(user -> {
            throw new BadRequestException("User already exists with username " + username);
        });

        User user = userMapper.toEntity(request);
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.password()));   // store only the BCrypt hash
        user = userRepository.save(user);

        return new AuthResponse(jwtService.generate(user.getId(), user.getUsername()), userMapper.toUserProfileResponse(user));
    }

    @Override
    public AuthResponse login(LoginRequest request) {
        String username = request.username().trim().toLowerCase();
        User user = userRepository.findByUsername(username)
                .filter(u -> u.getDeletedAt() == null)
                .filter(u -> passwordEncoder.matches(request.password(), u.getPassword()))
                // same message for "no such user" and "wrong password" so attackers can't tell which e-mails exist
                .orElseThrow(() -> new UnauthorizedException("Invalid username or password"));

        return new AuthResponse(jwtService.generate(user.getId(), user.getUsername()), userMapper.toUserProfileResponse(user));
    }
}
