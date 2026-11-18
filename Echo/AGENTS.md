# Contexto del Proyecto
- **Stack principal:** Backend en Java (ej. Spring Boot / Jakarta EE) y Frontend en HTML5, CSS3 y JavaScript (ej. Thymeleaf / JSP / archivos estáticos).
- **Base de datos (si aplica):** [Ej: MySQL / PostgreSQL / H2 / JPA-Hibernate — borra esta línea si no usas BD].
- **Estructura típica:**
  - Código fuente Java: `src/main/java` (o `src/`)
  - Vistas HTML y recursos: `src/main/resources/templates` y `src/main/resources/static` (o `webapp/`)
  - **Carpetas prohibidas (NO leer ni modificar):** `target/`, `build/`, `.git/`, `.idea/`, `.settings/`, `bin/`, `out/`.

# Comandos de compilación y ejecución
*(Nota para Codex: revisa `pom.xml` o `build.gradle` si existen para confirmar la herramienta exacta)*
- **Maven:** `mvn spring-boot:run` | Tests: `mvn test` | Compilar: `mvn clean package`
- **Gradle:** `./gradlew bootRun` | Tests: `./gradlew test`
- **Java puro (sin gestor):** `javac` / ejecución desde el IDE.

# Reglas estrictas para Codex (Ahorro de tokens y seguridad)
1. **Sé conciso:** Responde en español, de forma directa y sin explicaciones largas ni código de relleno.
2. **No leas archivos compilados:** Nunca analices `.class`, `.jar`, ni carpetas de salida (`target/`, `build/`).
3. **Plan antes de editar:** Antes de modificar más de 2 archivos Java o HTML, muéstrame qué archivos vas a tocar y qué harás exactamente. Espera mi aprobación.
4. **Respeta el código existente:** No refactorices, no cambies la indentación ni reorganices imports de clases o líneas que no tengan relación directa con mi petición.
5. **Dependencias:** No añadas nuevas dependencias al `pom.xml` / `build.gradle` ni importes librerías externas de CDN en el HTML sin preguntarme antes.
6. **Seguridad HTML/Java:** Si tocas formularios o endpoints, mantén las validaciones existentes y evita romper las rutas (`@RequestMapping`, `@GetMapping`, `@PostMapping` o `web.xml`).