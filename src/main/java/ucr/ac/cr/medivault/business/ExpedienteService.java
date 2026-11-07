package ucr.ac.cr.medivault.business;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ucr.ac.cr.medivault.controller.dto.*;
import ucr.ac.cr.medivault.data.*;
import ucr.ac.cr.medivault.domain.*;
import ucr.ac.cr.medivault.exception.*;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ExpedienteService {
    private final ExpedienteMedicoRepository expedientes;
    private final UsuarioRepository usuarios;

    public ExpedienteService(ExpedienteMedicoRepository expedientes, UsuarioRepository usuarios) {
        this.expedientes = expedientes;
        this.usuarios = usuarios;
    }

    @Transactional(readOnly = true)
    public ExpedienteResponseDto consultarPorId(Long id, String username) {
        var expediente = buscar(id);
        validarPropiedadRecurso(expediente, usuario(username));
        return mapToResponseDto(expediente);
    }

    @Transactional(readOnly = true)
    public List<ExpedienteResponseDto> listarExpedientesPropios(String username) {
        return expedientes.findByPacienteId(usuario(username).getId()).stream()
                .map(this::mapToResponseDto).toList();
    }

    @Transactional
    public ExpedienteResponseDto registrarExpediente(ExpedienteRequestDto dto, String username) {
        var doctor = usuario(username);
        if (doctor.getRol() != Rol.ROLE_DOCTOR && doctor.getRol() != Rol.ROLE_ADMIN) {
            throw new AccessDeniedSecurityException("Solo medicos autorizados pueden registrar expedientes.");
        }
        var paciente = usuarios.findById(dto.pacienteId())
                .orElseThrow(() -> new ResourceNotFoundException("Paciente no encontrado."));
        if (paciente.getRol() != Rol.ROLE_PACIENTE) {
            throw new IllegalArgumentException("El usuario indicado debe ser paciente.");
        }
        var e = new ExpedienteMedico();
        e.setPaciente(paciente);
        e.setMedicoAsignado(doctor);
        e.setCodigoExpediente(dto.codigoExpediente());
        e.setDiagnosticoPrincipal(dto.diagnosticoPrincipal());
        e.setNotasClinicas(dto.notasClinicas());
        e.setNivelConfidencialidad(dto.nivelConfidencialidad());
        e = expedientes.saveAndFlush(e);
        return mapToResponseDto(expedientes.findByIdWithDetails(e.getId()).orElseThrow());
    }

    @Transactional
    public void actualizarNotasDefensivo(Long id, String nuevasNotas, String username) {
        var expediente = buscar(id);
        var doctor = usuario(username);
        if (doctor.getRol() != Rol.ROLE_DOCTOR
                || !expediente.getMedicoAsignado().getId().equals(doctor.getId())) {
            throw new AccessDeniedSecurityException("Solo el medico asignado puede actualizar las notas.");
        }
        if (nuevasNotas == null || nuevasNotas.trim().length() < 10 || nuevasNotas.trim().length() > 4000
                || nuevasNotas.contains("<") || nuevasNotas.contains(">")) {
            throw new IllegalArgumentException("Notas: texto sin etiquetas, de 10 a 4000 caracteres.");
        }
        expediente.setNotasClinicas(nuevasNotas.trim());
        expediente.setFechaActualizacion(LocalDateTime.now());
    }

    private Usuario usuario(String username) {
        return usuarios.findByUsername(username)
                .orElseThrow(() -> new AccessDeniedSecurityException("Usuario no reconocido."));
    }

    private ExpedienteMedico buscar(Long id) {
        return expedientes.findByIdWithDetails(id)
                .orElseThrow(() -> new ResourceNotFoundException("Expediente no encontrado."));
    }

    private void validarPropiedadRecurso(ExpedienteMedico expediente, Usuario solicitante) {
        boolean permitido = switch (solicitante.getRol()) {
            case ROLE_ADMIN -> true;
            case ROLE_PACIENTE -> expediente.getPaciente().getId().equals(solicitante.getId());
            case ROLE_DOCTOR -> expediente.getMedicoAsignado().getId().equals(solicitante.getId());
        };
        if (!permitido) {
            throw new AccessDeniedSecurityException("Violacion IDOR: Acceso no autorizado.");
        }
    }

    private ExpedienteResponseDto mapToResponseDto(ExpedienteMedico e) {
        return new ExpedienteResponseDto(e.getId(), e.getCodigoExpediente(),
                e.getPaciente().getNombreCompleto(), e.getMedicoAsignado().getNombreCompleto(),
                e.getDiagnosticoPrincipal(), e.getNotasClinicas(), e.getNivelConfidencialidad(), e.getFechaRegistro());
    }
}
