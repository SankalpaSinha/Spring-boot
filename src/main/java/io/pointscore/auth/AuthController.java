package io.pointscore.auth;

import io.pointscore.auth.AuthDtos.LoginRequest;
import io.pointscore.auth.AuthDtos.MeResponse;
import io.pointscore.auth.AuthDtos.TokenResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public TokenResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request.email(), request.password());
    }

    /** Who the token says you are. Handy for clients and for debugging a 403. */
    @GetMapping("/me")
    public MeResponse me(Authentication authentication) {
        return AuthPrincipal.from(authentication)
                .map(MeResponse::from)
                .orElseThrow(() -> new UnauthenticatedException("authentication required"));
    }
}
