package Servidor;

import ServidorInterfaces.IServicioMensajesPrivados;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/** Guarda los mensajes privados en un CSV separado hasta confirmarse su entrega. */
public class GestorMensajesPrivadosCSV implements IServicioMensajesPrivados {
    private static final Path ARCHIVO = Paths.get("mensajes_privados.csv");
    private static final DateTimeFormatter FORMATO_FECHA = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public synchronized MensajePrivadoPendiente guardarPendiente(String remitente, String destinatario, String contenido) {
        String id = UUID.randomUUID().toString();
        String fechaHora = LocalDateTime.now().format(FORMATO_FECHA);
        String contenidoCodificado = Base64.getEncoder().encodeToString(contenido.getBytes(StandardCharsets.UTF_8));
        String linea = id + "|" + fechaHora + "|" + remitente + "|" + destinatario + "|PENDING|" + contenidoCodificado;
        try {
            Files.write(ARCHIVO, java.util.Collections.singletonList(linea), StandardCharsets.UTF_8,
                    java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar el mensaje privado.", e);
        }
        return new MensajePrivadoPendiente(id, fechaHora, remitente, destinatario, contenido);
    }

    @Override
    public synchronized List<MensajePrivadoPendiente> obtenerPendientes(String destinatario) {
        List<MensajePrivadoPendiente> pendientes = new ArrayList<>();
        if (!Files.exists(ARCHIVO)) return pendientes;
        try {
            for (String linea : Files.readAllLines(ARCHIVO, StandardCharsets.UTF_8)) {
                String[] partes = linea.split("\\|", 6);
                if (partes.length != 6 || !"PENDING".equals(partes[4]) || !destinatario.equals(partes[3])) continue;
                String contenido = new String(Base64.getDecoder().decode(partes[5]), StandardCharsets.UTF_8);
                pendientes.add(new MensajePrivadoPendiente(partes[0], partes[1], partes[2], partes[3], contenido));
            }
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("[Privados] No se pudieron leer todos los mensajes pendientes: " + e.getMessage());
        }
        return pendientes;
    }

    @Override
    public synchronized void marcarEntregado(String id, String destinatario) {
        if (!Files.exists(ARCHIVO)) return;
        Path temporal = ARCHIVO.resolveSibling(ARCHIVO.getFileName() + ".tmp");
        try {
            List<String> lineas = new ArrayList<>();
            for (String linea : Files.readAllLines(ARCHIVO, StandardCharsets.UTF_8)) {
                String[] partes = linea.split("\\|", 6);
                if (partes.length == 6 && id.equals(partes[0]) && destinatario.equals(partes[3])
                        && "PENDING".equals(partes[4])) {
                    partes[4] = "DELIVERED";
                    linea = String.join("|", partes);
                }
                lineas.add(linea);
            }
            Files.write(temporal, lineas, StandardCharsets.UTF_8);
            try {
                Files.move(temporal, ARCHIVO, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, ARCHIVO, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try { Files.deleteIfExists(temporal); } catch (IOException ignorada) {}
            throw new IllegalStateException("No se pudo confirmar la entrega del mensaje privado.", e);
        }
    }
}
