# Laboratorio 16 - MediVault Web

API REST con Spring Boot 3.3.4, Spring Security 6, Java 21, Maven y H2 en memoria.

## Ejecución

```powershell
$env:JAVA_HOME = "$env:USERPROFILE\.jdks\ms-21.0.8"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
mvn spring-boot:run
```

Dirección: `http://localhost:8080`. Los datos se reinician al arrancar.
Si se utiliza otro JDK 21, ajustar `JAVA_HOME`.

Usuarios: `admin_root`, `doctor_smith`, `doctora_vargas`, `paciente_carlos`, `paciente_maria` y `paciente_ana`.
Contraseña de prueba: `Password123!`.

## Endpoints

| Método | Ruta | Acceso |
| --- | --- | --- |
| POST | `/api/v1/auth/login` | Público; 5 peticiones por minuto por IP |
| GET | `/api/v1/expedientes/{id}` | Paciente propietario, médico asignado o administrador |
| GET | `/api/v1/expedientes/mis-expedientes` | Expedientes del paciente autenticado |
| POST | `/api/v1/expedientes` | Médico o administrador |
| PATCH | `/api/v1/expedientes/{id}/notas` | Solo médico asignado |

Enviar `Authorization: Bearer <token>` en las rutas de expedientes. Su límite es de 30 peticiones por minuto por IP.

```powershell
$login = Invoke-RestMethod http://localhost:8080/api/v1/auth/login -Method Post -ContentType 'application/json' -Body '{"username":"paciente_carlos","password":"Password123!"}'
$headers = @{ Authorization = "Bearer $($login.token)" }
Invoke-RestMethod http://localhost:8080/api/v1/expedientes/101 -Headers $headers
```

Cuerpo de actualización de notas:

```json
{"notasClinicas":"Paciente estable. Continuar seguimiento clinico."}
```

## Parte 2

El método vulnerable permitía modificar cualquier expediente sin comprobar el médico asignado ni validar el contenido. El PATCH exige autenticación, rol de médico, asignación al expediente y un DTO con notas de 10 a 4000 caracteres, sin etiquetas. Los intentos no autorizados reciben 403 y se auditan.

## Seguridad y reto

- JWT sin sesión, con expiración de una hora. Sin `JWT_SECRET`, se genera una clave aleatoria al arrancar; los tokens anteriores dejan de funcionar al reiniciar. Puede configurarse `JWT_SECRET` con al menos 32 bytes.
- CSP, `X-Frame-Options: DENY`, `nosniff` y caché deshabilitada. HSTS se envía en HTTPS.
- Errores con `ProblemDetail`, sin trazas ni consultas SQL en las respuestas.
- Bucket4j con cubos independientes por IP. La sexta petición de login recibe 429 y `Retry-After`.
- `X-Forwarded-For` se interpreta únicamente cuando la conexión procede de un proxy configurado en `MEDIVAULT_TRUSTED_PROXIES` (IPs o CIDR separados por comas). Sin proxy configurado, se utiliza la IP remota.
- `SecurityAuditorService` inserta `LOGIN_FAILED` e `IDOR_ATTEMPT_BLOCKED` de forma asíncrona, en transacciones independientes. Registra usuario, IP, ruta, estado y fecha; no registra contraseñas, tokens ni notas clínicas. No hay operaciones ni endpoints de modificación o eliminación de auditoría.

## Verificación

```powershell
mvn test
mvn package
```

Las pruebas verifican autenticación, propiedad, roles, notas, validación, cabeceras, JWT alterados, límites por IP y persistencia de auditoría.

Resultado: 13 pruebas aprobadas, sin errores. El JAR también se comprobó mediante HTTP: login y consulta propia 200, acceso ajeno 403, actualización autorizada 204 y sexto login 429 con `Retry-After`.
