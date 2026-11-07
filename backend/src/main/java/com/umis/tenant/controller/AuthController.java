package com.umis.tenant.controller;

import com.umis.common.api.ApiResponse;
import com.umis.security.UserPrincipal;
import com.umis.tenant.dto.AuthRequest;
import com.umis.tenant.dto.AuthResponse;
import com.umis.tenant.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for user authentication and token retrieval.
 */
@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Authentication", description = "Authentication and identity APIs")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and issue JWT bearer token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody AuthRequest request) {
        AuthResponse response = authService.authenticate(request);
        return ResponseEntity.ok(ApiResponse.ok("Authentication successful", response));
    }

    @GetMapping("/me")
    @Operation(summary = "Get current authenticated identity and active tenant context")
    public ResponseEntity<ApiResponse<UserPrincipal>> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.ok(principal));
    }
}
