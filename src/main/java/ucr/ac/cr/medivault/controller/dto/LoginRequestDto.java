package ucr.ac.cr.medivault.controller.dto;

import jakarta.validation.constraints.*;

public record LoginRequestDto(
    @NotBlank(message = "Usuario obligatorio.")
    @Size(min = 4, max = 50, message = "Usuario: 4 a 50 caracteres.")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "Solo letras, numeros y guion bajo.")
    String username,
    @NotBlank(message = "Contrasena requerida.")
    @Size(min = 8, max = 100, message = "Contrasena: 8 a 100 caracteres.")
    String password
) {}
