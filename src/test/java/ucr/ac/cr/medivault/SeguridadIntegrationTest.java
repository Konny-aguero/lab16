package ucr.ac.cr.medivault;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import ucr.ac.cr.medivault.data.UsuarioRepository;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "medivault.security.trusted-proxies=127.0.0.1,::1")
@AutoConfigureMockMvc
class SeguridadIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepository usuarios;
    @Autowired PasswordEncoder encoder;
    private static final AtomicInteger counter = new AtomicInteger();

    private String ip() { return "192.0.2." + counter.incrementAndGet(); }

    private MockHttpServletRequestBuilder request(String method, String path, String ip) {
        return switch (method) {
            case "POST" -> post(path).servletPath(path).header("X-Forwarded-For", ip);
            case "PATCH" -> patch(path).servletPath(path).header("X-Forwarded-For", ip);
            default -> get(path).servletPath(path).header("X-Forwarded-For", ip);
        };
    }

    private ResultActions login(String username, String password, String ip) throws Exception {
        return mvc.perform(request("POST", "/api/v1/auth/login", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", username, "password", password))));
    }

    private String token(String username) throws Exception {
        var response = login(username, "Password123!", ip()).andExpect(status().isOk())
                .andExpect(jsonPath("tokenType").value("Bearer"))
                .andReturn().getResponse();
        return json.readTree(response.getContentAsString()).get("token").asText();
    }

    private ResultActions consultar(long id, String token) throws Exception {
        return mvc.perform(request("GET", "/api/v1/expedientes/" + id, ip())
                .header("Authorization", "Bearer " + token));
    }

    @Test
    void todasLasCuentasPuedenAutenticarse() throws Exception {
        for (String user : new String[]{"admin_root", "doctor_smith", "doctora_vargas",
                "paciente_carlos", "paciente_maria", "paciente_ana"}) {
            assertThat(encoder.matches("Password123!", usuarios.findByUsername(user).orElseThrow().getPasswordHash()))
                    .isTrue();
            assertThat(token(user).split("\\.")).hasSize(3);
        }
    }

    @Test
    void pacienteSoloConsultaSusExpedientes() throws Exception {
        String carlos = token("paciente_carlos");
        consultar(101, carlos).andExpect(status().isOk()).andExpect(jsonPath("id").value(101))
                .andExpect(jsonPath("passwordHash").doesNotExist());
        consultar(102, carlos).andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("status").value(403));
        mvc.perform(request("GET", "/api/v1/expedientes/mis-expedientes", ip())
                .header("Authorization", "Bearer " + carlos))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(101));
    }

    @Test
    void medicoAsignadoYAdministradorPuedenConsultar() throws Exception {
        String doctor = token("doctor_smith");
        consultar(101, doctor).andExpect(status().isOk());
        consultar(103, doctor).andExpect(status().isOk());
        consultar(102, doctor).andExpect(status().isForbidden());
        consultar(102, token("admin_root")).andExpect(status().isOk());
    }

    @Test
    void idorQuedaAuditadoSinDatosClinicos() throws Exception {
        String origen = ip();
        mvc.perform(request("GET", "/api/v1/expedientes/101", origen)
                .header("Authorization", "Bearer " + token("paciente_maria")))
                .andExpect(status().isForbidden());
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            var rows = jdbc.queryForList("SELECT * FROM AUDIT_LOGS WHERE ip_origen = ?", origen);
            assertThat(rows).hasSize(1);
            assertThat(rows.getFirst()).containsEntry("USERNAME", "paciente_maria")
                    .containsEntry("ACCION", "IDOR_ATTEMPT_BLOCKED")
                    .containsEntry("CODIGO_ESTADO", 403)
                    .containsEntry("RECURSO_SOLICITADO", "/api/v1/expedientes/101")
                    .containsEntry("DETALLE_EVENTO", null);
            assertThat(rows.getFirst().get("FECHA_EVENTO")).isNotNull();
        });
    }

    @Test
    void credencialesInvalidasQuedanAuditadas() throws Exception {
        for (String username : new String[]{"admin_root", "usuario_inexistente"}) {
            String origen = ip();
            login(username, "Incorrecta123!", origen).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("detail").value("Credenciales invalidas."));
            await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
                var row = jdbc.queryForMap("SELECT * FROM AUDIT_LOGS WHERE ip_origen = ?", origen);
                assertThat(row).containsEntry("USERNAME", username).containsEntry("ACCION", "LOGIN_FAILED")
                        .containsEntry("CODIGO_ESTADO", 401).containsEntry("DETALLE_EVENTO", null);
                assertThat(row.toString()).doesNotContain("Incorrecta123!");
            });
        }
    }

    @Test
    void sextoLoginSeBloqueaSinAfectarOtraIp() throws Exception {
        String origen = ip();
        for (int i = 0; i < 5; i++) {
            login("admin_root", "Incorrecta123!", origen).andExpect(status().isUnauthorized());
        }
        login("admin_root", "Incorrecta123!", origen).andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(jsonPath("status").value(429));
        login("admin_root", "Password123!", ip()).andExpect(status().isOk());
    }

    @Test
    void consultasTienenLimiteDeTreintaPorIp() throws Exception {
        String origen = ip();
        String token = token("paciente_carlos");
        for (int i = 0; i < 30; i++) {
            mvc.perform(request("GET", "/api/v1/expedientes/101", origen)
                    .header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        }
        mvc.perform(request("GET", "/api/v1/expedientes/101", origen)
                .header("Authorization", "Bearer " + token)).andExpect(status().isTooManyRequests());
        consultar(101, token).andExpect(status().isOk());
    }

    @Test
    void noConfiaEnForwardedForDeClientesDirectos() throws Exception {
        String remote = ip();
        for (int i = 0; i < 6; i++) {
            mvc.perform(request("POST", "/api/v1/auth/login", ip())
                    .with(req -> { req.setRemoteAddr(remote); return req; })
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"username\":\"admin_root\",\"password\":\"Incorrecta123!\"}"))
                    .andExpect(status().is(i < 5 ? 401 : 429));
        }
    }

    @Test
    void cabecerasDefensivasYSesionSinEstado() throws Exception {
        String token = token("paciente_carlos");
        mvc.perform(request("GET", "/api/v1/expedientes/101", ip()).secure(true)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("Content-Security-Policy", containsString("frame-ancestors 'none'")))
                .andExpect(header().string("Strict-Transport-Security", "max-age=31536000 ; includeSubDomains"))
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void rechazaPeticionesSinTokenYTokensAlterados() throws Exception {
        mvc.perform(request("GET", "/api/v1/expedientes/101", ip()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("status").value(401));
        consultar(101, "token.invalido.firma").andExpect(status().isUnauthorized());
        String valid = token("paciente_carlos");
        String[] parts = valid.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        consultar(101, String.join(".", parts)).andExpect(status().isUnauthorized());
        mvc.perform(request("GET", "/h2-console/index.html", ip()).header("Authorization", "Bearer " + valid))
                .andExpect(status().isForbidden());
    }

    @Test
    void validacionYErroresNoExponenInternos() throws Exception {
        login("' OR 1=1 --", "Password123!", ip()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("invalid_fields.username").exists());
        mvc.perform(request("POST", "/api/v1/auth/login", ip()).contentType(MediaType.APPLICATION_JSON)
                .content("{invalid"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("trace").doesNotExist());
        String token = token("doctor_smith");
        consultar(99999, token).andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Hibernate"))));
        mvc.perform(request("GET", "/api/v1/expedientes/abc", ip())
                .header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
        mvc.perform(request("POST", "/api/v1/expedientes", ip()).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("invalid_fields.pacienteId").exists());
    }

    @Test
    void soloMedicoAsignadoActualizaNotasConDtoValido() throws Exception {
        String path = "/api/v1/expedientes/103/notas";
        String body = "{\"notasClinicas\":\"  Control clinico satisfactorio.  \"}";
        for (String user : new String[]{"paciente_ana", "doctora_vargas", "admin_root"}) {
            mvc.perform(request("PATCH", path, ip()).header("Authorization", "Bearer " + token(user))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        }
        String doctor = token("doctor_smith");
        for (String notes : new String[]{"corta", "          ", "<script>alert(1)</script>", "x".repeat(4001)}) {
            mvc.perform(request("PATCH", path, ip()).header("Authorization", "Bearer " + doctor)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(Map.of("notasClinicas", notes))))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(request("PATCH", path, ip()).header("Authorization", "Bearer " + doctor)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNoContent());
        consultar(103, doctor).andExpect(jsonPath("notasClinicas").value("Control clinico satisfactorio."));
        assertThat(jdbc.queryForObject("SELECT fecha_actualizacion FROM EXPEDIENTES_MEDICOS WHERE id = 103",
                java.sql.Timestamp.class)).isNotNull();
    }

    @Test
    void crearExpedienteValidaRolYConflictos() throws Exception {
        String body = json.writeValueAsString(Map.of("pacienteId", 4, "codigoExpediente", "EXP-2026-999",
                "diagnosticoPrincipal", "Diagnostico de prueba", "notasClinicas", "Notas clinicas de prueba.",
                "nivelConfidencialidad", "ALTO"));
        mvc.perform(request("POST", "/api/v1/expedientes", ip())
                .header("Authorization", "Bearer " + token("paciente_carlos"))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        String doctor = token("doctor_smith");
        var response = mvc.perform(request("POST", "/api/v1/expedientes", ip())
                .header("Authorization", "Bearer " + doctor).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(jsonPath("codigoExpediente").value("EXP-2026-999"))
                .andExpect(jsonPath("fechaRegistro").isNotEmpty()).andReturn().getResponse();
        long id = json.readTree(response.getContentAsString()).get("id").asLong();
        mvc.perform(request("POST", "/api/v1/expedientes", ip()).header("Authorization", "Bearer " + doctor)
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isConflict())
                .andExpect(content().string(not(containsString("EXPEDIENTES_MEDICOS"))));
        jdbc.update("DELETE FROM EXPEDIENTES_MEDICOS WHERE id = ?", id);
    }
}
