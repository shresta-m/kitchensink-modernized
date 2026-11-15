package com.kitchensink.controller;



import com.kitchensink.controller.contract.AuthControllerInterface;
import com.kitchensink.dto.auth.AdminRegisterRequest;
import com.kitchensink.dto.auth.LoginRequest;
import com.kitchensink.dto.auth.LoginResponse;
import com.kitchensink.dto.auth.RegisterRequest;
import com.kitchensink.dto.user.UserResponse;
import com.kitchensink.service.AuthService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController implements AuthControllerInterface {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @Override
    public LoginResponse login(LoginRequest body) {
        return LoginResponse.from(auth.login(body.username(), body.password()));
    }

    @Override
    public ResponseEntity<LoginResponse> register(RegisterRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(LoginResponse.from(auth.register(body.username(), body.password())));
    }

    @Override
    public ResponseEntity<UserResponse> registerAdmin(AdminRegisterRequest body) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(UserResponse.from(auth.registerAdmin(body.username(), body.password(), body.adminCode())));
    }

    @Override
    public LoginResponse.UserInfo me(@AuthenticationPrincipal Jwt jwt) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return new LoginResponse.UserInfo(jwt.getSubject(), roles == null || roles.isEmpty() ? "" : roles.get(0));
    }
}
