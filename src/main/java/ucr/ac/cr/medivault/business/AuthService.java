package ucr.ac.cr.medivault.business;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ucr.ac.cr.medivault.controller.dto.*;
import ucr.ac.cr.medivault.data.UsuarioRepository;
import ucr.ac.cr.medivault.security.JwtTokenProvider;

@Service
public class AuthService {
    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtTokenProvider tokens;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtTokenProvider tokens) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public LoginResponseDto autenticar(LoginRequestDto request) {
        var usuario = usuarios.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Credenciales invalidas."));
        if (!encoder.matches(request.password(), usuario.getPasswordHash())
                || !"ACTIVO".equals(usuario.getEstado()) || usuario.getCuentaBloqueada()) {
            throw new BadCredentialsException("Credenciales invalidas.");
        }
        return new LoginResponseDto(tokens.generateToken(usuario.getUsername(), usuario.getRol().name()),
                "Bearer", tokens.getExpirationMs(), usuario.getUsername(), usuario.getRol().name());
    }
}
