package com.farhaan.projects.Ace.controllers;

import com.farhaan.projects.Ace.Dto.auth.AuthResponse;
import com.farhaan.projects.Ace.Dto.auth.LoginRequest;
import com.farhaan.projects.Ace.Dto.auth.SignUpRequest;
import com.farhaan.projects.Ace.Dto.auth.UserProfileResponse;
import com.farhaan.projects.Ace.security.CurrentUser;
import com.farhaan.projects.Ace.services.AuthService;
import com.farhaan.projects.Ace.services.UserService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping(path = "/api/auth")
@FieldDefaults(makeFinal = true, level = AccessLevel.PRIVATE)
public class AuthController {
    AuthService authService;
    UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<AuthResponse> signup(@RequestBody @Valid SignUpRequest request) {
        return ResponseEntity.ok(authService.signup(request));

    }

    @PostMapping("/login")   // was "@PostMapping" on /api/auth - now a proper /api/auth/login
    public ResponseEntity<AuthResponse> login(@RequestBody @Valid LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getProfile() {
        return ResponseEntity.ok(userService.getProfile(CurrentUser.id()));
    }

}
