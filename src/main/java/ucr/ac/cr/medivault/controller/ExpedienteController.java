package ucr.ac.cr.medivault.controller;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import ucr.ac.cr.medivault.business.ExpedienteService;
import ucr.ac.cr.medivault.controller.dto.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/expedientes")
public class ExpedienteController {
    private final ExpedienteService expedientes;

    public ExpedienteController(ExpedienteService expedientes) { this.expedientes = expedientes; }

    @GetMapping("/{id}")
    public ExpedienteResponseDto obtenerPorId(@PathVariable Long id, Authentication auth) {
        return expedientes.consultarPorId(id, auth.getName());
    }

    @GetMapping("/mis-expedientes")
    public List<ExpedienteResponseDto> obtenerMisExpedientes(Authentication auth) {
        return expedientes.listarExpedientesPropios(auth.getName());
    }

    @PostMapping
    public ResponseEntity<ExpedienteResponseDto> crearExpediente(
            @Valid @RequestBody ExpedienteRequestDto dto, Authentication auth) {
        return ResponseEntity.status(HttpStatus.CREATED).body(expedientes.registrarExpediente(dto, auth.getName()));
    }

    @PatchMapping("/{id}/notas")
    public ResponseEntity<Void> actualizarNotas(@PathVariable Long id,
            @Valid @RequestBody NotasRequestDto dto, Authentication auth) {
        expedientes.actualizarNotasDefensivo(id, dto.notasClinicas(), auth.getName());
        return ResponseEntity.noContent().build();
    }
}
