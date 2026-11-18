package Servidor;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayInputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Puente HTTP local entre la interfaz web y las sesiones TCP de Echo. */
public final class EchoWebServer implements AutoCloseable {
    private static final int TCP_PORT = 5000;
    private static final int WEB_PORT = 8080;
    private static final int MAX_AVATAR_BYTES = 4 * 1024 * 1024;
    private static final int MAX_AVATAR_DIMENSION = 4096;
    private static final long MAX_AVATAR_PIXELS = 16_000_000L;
    private static final int AVATAR_SIZE = 512;
    private static final long SESSION_IDLE_MS = TimeUnit.MINUTES.toMillis(30);
    private final Map<String, BrowserSession> sessions = new ConcurrentHashMap<>();
    private final Path webRoot = Paths.get("web").toAbsolutePath().normalize();
    private final Path avatarRoot = Paths.get("profile-photos").toAbsolutePath().normalize();
    private HttpServer server;
    private ExecutorService httpExecutor;
    private java.util.concurrent.ScheduledExecutorService reaper;

    public void iniciar() throws IOException {
        if (!Files.isDirectory(webRoot)) throw new IOException("No se encontró la carpeta web: " + webRoot);
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), WEB_PORT), 0);
        httpExecutor = Executors.newFixedThreadPool(12, runnable -> {
            Thread thread = new Thread(runnable, "Echo-Web-Request");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(httpExecutor);
        server.createContext("/api/session", this::handleSession);
        server.createContext("/api/command", this::handleCommand);
        server.createContext("/api/events", this::handleEvents);
        server.createContext("/api/avatar", this::handleAvatar);
        server.createContext("/", this::handleStaticFile);
        server.start();

        reaper = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "Echo-Web-Session-Reaper");
            thread.setDaemon(true);
            return thread;
        });
        reaper.scheduleAtFixedRate(this::removeExpiredSessions, 1, 1, TimeUnit.MINUTES);
        System.out.println("Interfaz web Echo disponible en http://127.0.0.1:" + WEB_PORT);
    }

    private void handleSession(HttpExchange exchange) throws IOException {
        if (!"/api/session".equals(exchange.getRequestURI().getPath())) {
            sendJson(exchange, 404, "{\"error\":\"Ruta no encontrada.\"}");
            return;
        }
        if ("POST".equals(exchange.getRequestMethod())) {
            if (!isSameOrigin(exchange)) {
                sendJson(exchange, 403, "{\"error\":\"Origen no permitido.\"}");
                return;
            }
            removeExpiredSessions();
            try {
                BrowserSession session = new BrowserSession();
                String id = UUID.randomUUID().toString();
                sessions.put(id, session);
                sendJson(exchange, 201, "{\"id\":" + jsonString(id) + "}");
            } catch (IOException e) {
                sendJson(exchange, 503, "{\"error\":\"El servidor TCP no está disponible.\"}");
            }
            return;
        }

        if ("DELETE".equals(exchange.getRequestMethod())) {
            if (!isSameOrigin(exchange)) {
                sendJson(exchange, 403, "{\"error\":\"Origen no permitido.\"}");
                return;
            }
            Map<String, String> query;
            try { query = parseForm(exchange.getRequestURI().getRawQuery()); }
            catch (IOException e) {
                sendJson(exchange, 400, "{\"error\":\"Formulario inválido.\"}");
                return;
            }
            String id = query.get("id");
            BrowserSession session = id == null ? null : sessions.remove(id);
            if (session != null) session.close();
            sendJson(exchange, 200, "{\"closed\":true}");
            return;
        }

        methodNotAllowed(exchange, "POST, DELETE");
    }

    private void handleCommand(HttpExchange exchange) throws IOException {
        if (!"/api/command".equals(exchange.getRequestURI().getPath())) {
            sendJson(exchange, 404, "{\"error\":\"Ruta no encontrada.\"}");
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "POST");
            return;
        }
        if (!isSameOrigin(exchange)) {
            sendJson(exchange, 403, "{\"error\":\"Origen no permitido.\"}");
            return;
        }
        Map<String, String> form;
        try { form = parseForm(readBody(exchange, 8192)); }
        catch (IOException e) {
            sendJson(exchange, 400, "{\"error\":\"Formulario inválido o demasiado grande.\"}");
            return;
        }
        String id = form.get("id");
        BrowserSession session = id == null ? null : sessions.get(id);
        String command = form.get("command");
        if (session == null) {
            sendJson(exchange, 404, "{\"error\":\"La sesión web ha caducado.\"}");
            return;
        }
        if (!isAllowedCommand(command)) {
            sendJson(exchange, 400, "{\"error\":\"Acción no válida.\"}");
            return;
        }
        try {
            session.send(command);
            sendJson(exchange, 202, "{\"accepted\":true}");
        } catch (IOException e) {
            sessions.remove(id, session);
            session.close();
            sendJson(exchange, 410, "{\"error\":\"Se perdió la conexión TCP.\"}");
        }
    }

    private void handleEvents(HttpExchange exchange) throws IOException {
        if (!"/api/events".equals(exchange.getRequestURI().getPath())) {
            sendJson(exchange, 404, "{\"error\":\"Ruta no encontrada.\"}");
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "GET");
            return;
        }
        Map<String, String> query;
        try { query = parseForm(exchange.getRequestURI().getRawQuery()); }
        catch (IOException e) {
            sendJson(exchange, 400, "{\"error\":\"Formulario inválido.\"}");
            return;
        }
        String id = query.get("id");
        BrowserSession session = id == null ? null : sessions.get(id);
        if (session == null) {
            sendJson(exchange, 404, "{\"error\":\"La sesión web ha caducado.\"}");
            return;
        }

        List<String> events = session.drainEvents();
        StringBuilder json = new StringBuilder("{\"connected\":").append(session.isConnected())
                .append(",\"events\":[");
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) json.append(',');
            json.append(jsonString(events.get(i)));
        }
        json.append("]}");
        sendJson(exchange, 200, json.toString());
    }

    private void handleAvatar(HttpExchange exchange) throws IOException {
        if (!"/api/avatar".equals(exchange.getRequestURI().getPath())) {
            sendJson(exchange, 404, "{\"error\":\"Ruta no encontrada.\"}");
            return;
        }
        Map<String, String> query;
        try { query = parseForm(exchange.getRequestURI().getRawQuery()); }
        catch (IOException e) {
            sendJson(exchange, 400, "{\"error\":\"Parámetros inválidos.\"}");
            return;
        }
        String id = query.get("id");
        String username = query.get("user");
        BrowserSession session = id == null ? null : sessions.get(id);
        if (session == null || !session.canReadAvatars()) {
            sendJson(exchange, 401, "{\"error\":\"Inicia sesión para ver las fotos de perfil.\"}");
            return;
        }
        if (username == null || username.length() > 40 || username.indexOf('|') >= 0
                || username.indexOf('\r') >= 0 || username.indexOf('\n') >= 0) {
            sendJson(exchange, 400, "{\"error\":\"Usuario no válido.\"}");
            return;
        }

        if ("GET".equals(exchange.getRequestMethod())) {
            Path avatar = avatarPath(username);
            if (!Files.isRegularFile(avatar)) {
                sendJson(exchange, 404, "{\"error\":\"No hay foto de perfil.\"}");
                return;
            }
            byte[] image = Files.readAllBytes(avatar);
            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.getResponseHeaders().set("Cache-Control", "private, max-age=60");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.sendResponseHeaders(200, image.length);
            try (OutputStream output = exchange.getResponseBody()) { output.write(image); }
            return;
        }

        if (!session.isOwnProfile(username)) {
            sendJson(exchange, 403, "{\"error\":\"Solo puedes cambiar tu propia foto.\"}");
            return;
        }
        if (!isSameOrigin(exchange)) {
            sendJson(exchange, 403, "{\"error\":\"Origen no permitido.\"}");
            return;
        }
        if ("DELETE".equals(exchange.getRequestMethod())) {
            Files.deleteIfExists(avatarPath(username));
            sendJson(exchange, 200, "{\"removed\":true}");
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "GET, POST, DELETE");
            return;
        }

        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType == null || !(contentType.toLowerCase(java.util.Locale.ROOT).startsWith("image/png")
                || contentType.toLowerCase(java.util.Locale.ROOT).startsWith("image/jpeg"))) {
            sendJson(exchange, 415, "{\"error\":\"Elige una imagen PNG o JPEG.\"}");
            return;
        }
        byte[] uploaded;
        try { uploaded = readBodyBytes(exchange, MAX_AVATAR_BYTES); }
        catch (IOException e) {
            sendJson(exchange, 413, "{\"error\":\"La foto supera el límite de 4 MB.\"}");
            return;
        }
        BufferedImage source;
        try { source = decodeAvatar(uploaded); }
        catch (IOException | IllegalArgumentException e) {
            sendJson(exchange, 400, "{\"error\":\"La imagen no es válida o tiene demasiada resolución.\"}");
            return;
        }
        BufferedImage thumbnail = cropAvatar(source);
        try {
            Files.createDirectories(avatarRoot);
            Path destination = avatarPath(username);
            Path temporary = avatarRoot.resolve(destination.getFileName() + ".tmp-" + UUID.randomUUID());
            try {
                if (!ImageIO.write(thumbnail, "png", temporary.toFile())) {
                    throw new IOException("No se pudo codificar la miniatura PNG.");
                }
                try {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
                }
            } finally {
                Files.deleteIfExists(temporary);
            }
            sendJson(exchange, 201, "{\"saved\":true}");
        } catch (IOException e) {
            sendJson(exchange, 500, "{\"error\":\"No se pudo guardar la foto en este equipo.\"}");
        } finally {
            thumbnail.flush();
        }
    }

    private static BufferedImage decodeAvatar(byte[] uploaded) throws IOException {
        if (uploaded.length == 0 || uploaded.length > MAX_AVATAR_BYTES) {
            throw new IllegalArgumentException("Tamaño de imagen no válido.");
        }
        try (ImageInputStream imageInput = ImageIO.createImageInputStream(new ByteArrayInputStream(uploaded))) {
            if (imageInput == null) throw new IllegalArgumentException("Imagen no reconocida.");
            java.util.Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw new IllegalArgumentException("Formato de imagen no compatible.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_AVATAR_DIMENSION || height > MAX_AVATAR_DIMENSION
                        || (long) width * height > MAX_AVATAR_PIXELS) {
                    throw new IllegalArgumentException("Resolución demasiado grande.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new IllegalArgumentException("Imagen vacía.");
                return image;
            } finally {
                reader.dispose();
            }
        }
    }

    private static BufferedImage cropAvatar(BufferedImage source) {
        BufferedImage square = new BufferedImage(AVATAR_SIZE, AVATAR_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = square.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            double scale = Math.max((double) AVATAR_SIZE / source.getWidth(), (double) AVATAR_SIZE / source.getHeight());
            int width = (int) Math.ceil(source.getWidth() * scale);
            int height = (int) Math.ceil(source.getHeight() * scale);
            graphics.drawImage(source, (AVATAR_SIZE - width) / 2, (AVATAR_SIZE - height) / 2, width, height, null);
        } finally {
            graphics.dispose();
            source.flush();
        }
        return square;
    }

    private Path avatarPath(String username) {
        return avatarRoot.resolve(sha256(username) + ".png").normalize();
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder encoded = new StringBuilder(digest.length * 2);
            for (byte item : digest) encoded.append(String.format("%02x", item & 0xff));
            return encoded.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible.", e);
        }
    }

    private void handleStaticFile(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
            methodNotAllowed(exchange, "GET, HEAD");
            return;
        }
        String requestPath = exchange.getRequestURI().getPath();
        if (requestPath == null || requestPath.equals("/")) requestPath = "/index.html";
        Path requested = webRoot.resolve(requestPath.substring(1)).normalize();
        if (!requested.startsWith(webRoot) || !Files.isRegularFile(requested)) {
            sendText(exchange, 404, "Página no encontrada.", "text/plain; charset=UTF-8");
            return;
        }

        String fileName = requested.getFileName().toString();
        String contentType = "text/plain; charset=UTF-8";
        if (fileName.endsWith(".html")) contentType = "text/html; charset=UTF-8";
        else if (fileName.endsWith(".css")) contentType = "text/css; charset=UTF-8";
        else if (fileName.endsWith(".js")) contentType = "text/javascript; charset=UTF-8";
        else if (fileName.endsWith(".svg")) contentType = "image/svg+xml; charset=UTF-8";

        byte[] content = Files.readAllBytes(requested);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Content-Security-Policy", "default-src 'self'; style-src 'self'; script-src 'self'; connect-src 'self'; img-src 'self' data:; font-src 'self'");
        if ("HEAD".equals(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(200, -1);
        } else {
            exchange.sendResponseHeaders(200, content.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(content);
            }
        }
        exchange.close();
    }

    private static boolean isAllowedCommand(String command) {
        if (command == null || command.isEmpty() || command.length() > 6000
                || command.indexOf('\n') >= 0 || command.indexOf('\r') >= 0) return false;
        String type = command.contains("|") ? command.substring(0, command.indexOf('|')) : command;
        return "REG".equals(type) || "LOGIN".equals(type) || "CHANGE_PASSWORD".equals(type)
                || "JOIN_ROOM".equals(type) || "LEAVE_ROOM".equals(type) || "MSG_ROOM".equals(type)
                || "LIST_USERS".equals(type) || "MSG_PRIV".equals(type) || "ACK_PRIV".equals(type)
                || "LOGOUT".equals(type) || "GET_HIST".equals(type) || "PING".equals(type);
    }

    private static boolean isSameOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        return origin == null || "http://127.0.0.1:8080".equals(origin);
    }

    private void removeExpiredSessions() {
        long cutoff = System.currentTimeMillis() - SESSION_IDLE_MS;
        for (Map.Entry<String, BrowserSession> entry : sessions.entrySet()) {
            BrowserSession session = entry.getValue();
            if (session.lastActivity() < cutoff && sessions.remove(entry.getKey(), session)) session.close();
        }
    }

    private static String readBody(HttpExchange exchange, int maximumBytes) throws IOException {
        try (InputStream input = exchange.getRequestBody(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[1024];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maximumBytes) throw new IOException("Cuerpo de petición demasiado grande.");
                output.write(buffer, 0, read);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static byte[] readBodyBytes(HttpExchange exchange, int maximumBytes) throws IOException {
        String contentLength = exchange.getRequestHeaders().getFirst("Content-Length");
        if (contentLength != null) {
            try {
                if (Long.parseLong(contentLength) > maximumBytes) throw new IOException("Cuerpo demasiado grande.");
            } catch (NumberFormatException e) {
                throw new IOException("Longitud inválida.", e);
            }
        }
        try (InputStream input = exchange.getRequestBody(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maximumBytes) throw new IOException("Cuerpo demasiado grande.");
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static Map<String, String> parseForm(String encoded) throws IOException {
        Map<String, String> values = new HashMap<>();
        if (encoded == null || encoded.isEmpty()) return values;
        try {
            for (String pair : encoded.split("&")) {
                int separator = pair.indexOf('=');
                String key = separator < 0 ? pair : pair.substring(0, separator);
                String value = separator < 0 ? "" : pair.substring(separator + 1);
                values.put(URLDecoder.decode(key, "UTF-8"), URLDecoder.decode(value, "UTF-8"));
            }
            return values;
        } catch (IllegalArgumentException e) {
            throw new IOException("Formulario inválido.", e);
        }
    }

    private static String jsonString(String value) {
        StringBuilder json = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char character = value.charAt(i);
            switch (character) {
                case '"': json.append("\\\""); break;
                case '\\': json.append("\\\\"); break;
                case '\n': json.append("\\n"); break;
                case '\r': json.append("\\r"); break;
                case '\t': json.append("\\t"); break;
                default:
                    if (character < 0x20) json.append(String.format("\\u%04x", (int) character));
                    else json.append(character);
            }
        }
        return json.append('"').toString();
    }

    private static void methodNotAllowed(HttpExchange exchange, String allowed) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        sendJson(exchange, 405, "{\"error\":\"Método no permitido.\"}");
    }

    private static void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        sendText(exchange, status, json, "application/json; charset=UTF-8");
    }

    private static void sendText(HttpExchange exchange, int status, String text, String contentType) throws IOException {
        byte[] body = text.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    @Override
    public void close() {
        for (BrowserSession session : sessions.values()) session.close();
        sessions.clear();
        if (server != null) server.stop(1);
        if (httpExecutor != null) httpExecutor.shutdownNow();
        if (reaper != null) reaper.shutdownNow();
    }

    private static final class BrowserSession {
        private final Socket socket;
        private final java.io.BufferedReader input;
        private final java.io.PrintWriter output;
        private final ArrayBlockingQueue<String> events = new ArrayBlockingQueue<>(1000);
        private volatile boolean connected = true;
        private volatile boolean closed;
        private volatile long lastActivity = System.currentTimeMillis();
        private volatile String loginCandidate;
        private volatile String authenticatedUser;
        private volatile boolean passwordResetRequired;

        private BrowserSession() throws IOException {
            socket = new Socket(InetAddress.getByName("127.0.0.1"), TCP_PORT);
            input = new java.io.BufferedReader(new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            output = new java.io.PrintWriter(new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            Thread reader = new Thread(this::readEvents, "Echo-Web-TCP-Listener");
            reader.setDaemon(true);
            reader.start();
        }

        private synchronized void send(String command) throws IOException {
            if (!connected || closed || socket.isClosed()) throw new IOException("Conexión TCP cerrada.");
            if (command.startsWith("LOGIN|")) {
                String[] parts = command.split("\\|", 3);
                loginCandidate = parts.length >= 2 ? parts[1] : null;
            }
            output.println(command);
            if (output.checkError()) throw new IOException("No se pudo escribir en el socket TCP.");
            touch();
        }

        private void readEvents() {
            try {
                String event;
                while ((event = input.readLine()) != null) {
                    if (event.startsWith("LOGIN_OK|")) {
                        authenticatedUser = loginCandidate;
                        passwordResetRequired = event.startsWith("LOGIN_OK|RESET_REQUIRED");
                    } else if ("PASSWORD_UPDATED".equals(event)) {
                        passwordResetRequired = false;
                    }
                    offerEvent(event);
                    touch();
                }
            } catch (IOException e) {
                if (!closed) offerEvent("CONNECTION_CLOSED|");
            } finally {
                connected = false;
            }
        }

        private void offerEvent(String event) {
            if (!events.offer(event)) {
                events.poll();
                events.offer(event);
            }
        }

        private List<String> drainEvents() {
            List<String> batch = new ArrayList<>();
            events.drainTo(batch, 250);
            touch();
            return batch;
        }

        private boolean isConnected() { return connected && !closed; }
        private boolean canReadAvatars() { return authenticatedUser != null && !passwordResetRequired && isConnected(); }
        private boolean isOwnProfile(String username) { return canReadAvatars() && username.equals(authenticatedUser); }
        private long lastActivity() { return lastActivity; }
        private void touch() { lastActivity = System.currentTimeMillis(); }

        private void close() {
            closed = true;
            connected = false;
            try { socket.close(); } catch (IOException ignored) { }
        }
    }
}
