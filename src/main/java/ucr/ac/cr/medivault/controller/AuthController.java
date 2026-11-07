package ucr.ac.cr.medivault.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import ucr.ac.cr.medivault.business.AuthService;
import ucr.ac.cr.medivault.controller.dto.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;

    public AuthController(AuthService auth) { this.auth = auth; }

    @PostMapping("/login")
    public LoginResponseDto login(@Valid @RequestBody LoginRequestDto dto, HttpServletRequest request) {
        request.setAttribute("loginUsername", dto.username());
        return auth.autenticar(dto);
    }
}
