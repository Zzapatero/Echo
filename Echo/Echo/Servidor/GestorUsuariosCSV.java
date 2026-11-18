package Servidor;

import ServidorInterfaces.IServicioUsuarios;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

/** Usuarios persistidos en CSV con migración de claves heredadas a PBKDF2. */
public class GestorUsuariosCSV implements IServicioUsuarios {
    private static final Path ARCHIVO_USUARIOS = Paths.get("usuarios.csv");
    private final ConcurrentHashMap<String, String> usuariosRegistrados = new ConcurrentHashMap<>();

    public GestorUsuariosCSV() {
        cargarDesdeCSV();
    }

    private void cargarDesdeCSV() {
        if (!Files.exists(ARCHIVO_USUARIOS)) return;
        boolean migrado = false;
        try {
            for (String linea : Files.readAllLines(ARCHIVO_USUARIOS, StandardCharsets.UTF_8)) {
                String[] partes = linea.split(",", 2);
                if (partes.length != 2 || partes[0].trim().isEmpty()) continue;
                String almacenada = partes[1];
                if (!SeguridadCredenciales.esHash(almacenada)) {
                    almacenada = SeguridadCredenciales.hash(almacenada, true);
                    migrado = true;
                }
                usuariosRegistrados.put(partes[0], almacenada);
            }
            if (migrado) persistir();
            System.out.println("[Usuarios] Cuentas cargadas; credenciales protegidas con PBKDF2.");
        } catch (IOException e) {
            System.err.println("[Usuarios] Error al cargar el archivo CSV: " + e.getMessage());
        }
    }

    @Override
    public boolean existeUsuario(String nombre) {
        return usuariosRegistrados.containsKey(nombre);
    }

    @Override
    public boolean validarCredenciales(String nombre, String clave) {
        String almacenada = usuariosRegistrados.get(nombre);
        if (almacenada == null) return false;
        if (SeguridadCredenciales.esHash(almacenada)) return SeguridadCredenciales.verificar(clave, almacenada);

        // Compatibilidad defensiva por si aparece un CSV legado tras el arranque.
        if (!almacenada.equals(clave)) return false;
        synchronized (this) {
            if (almacenada.equals(usuariosRegistrados.get(nombre))) {
                usuariosRegistrados.put(nombre, SeguridadCredenciales.hash(clave, true));
                persistir();
            }
        }
        return true;
    }

    @Override
    public synchronized boolean registrarUsuario(String nombre, String clave) {
        if (!claveValida(clave) || usuariosRegistrados.containsKey(nombre)) return false;
        usuariosRegistrados.put(nombre, SeguridadCredenciales.hash(clave, false));
        try {
            persistir();
            return true;
        } catch (IllegalStateException e) {
            usuariosRegistrados.remove(nombre);
            return false;
        }
    }

    @Override
    public boolean requiereCambioClave(String nombre) {
        return SeguridadCredenciales.requiereCambio(usuariosRegistrados.get(nombre));
    }

    @Override
    public synchronized boolean cambiarClave(String nombre, String clave) {
        if (!claveValida(clave) || !usuariosRegistrados.containsKey(nombre)) return false;
        String anterior = usuariosRegistrados.put(nombre, SeguridadCredenciales.hash(clave, false));
        try {
            persistir();
            return true;
        } catch (IllegalStateException e) {
            usuariosRegistrados.put(nombre, anterior);
            return false;
        }
    }

    private boolean claveValida(String clave) {
        return clave != null && clave.length() >= 12 && clave.indexOf('|') < 0 && clave.indexOf(',') < 0
                && clave.indexOf('\n') < 0 && clave.indexOf('\r') < 0;
    }

    @Override
    public java.util.Set<String> listarUsuarios() {
        return new TreeSet<>(usuariosRegistrados.keySet());
    }

    private void persistir() {
        Path temporal = ARCHIVO_USUARIOS.resolveSibling(ARCHIVO_USUARIOS.getFileName() + ".tmp");
        List<String> lineas = new ArrayList<>();
        for (Map.Entry<String, String> entrada : new TreeMap<>(usuariosRegistrados).entrySet()) {
            lineas.add(entrada.getKey() + "," + entrada.getValue());
        }
        try {
            Files.write(temporal, lineas, StandardCharsets.UTF_8);
            try {
                Files.move(temporal, ARCHIVO_USUARIOS, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporal, ARCHIVO_USUARIOS, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            try { Files.deleteIfExists(temporal); } catch (IOException ignorada) {}
            throw new IllegalStateException("No se pudo guardar usuarios.csv.", e);
        }
    }
}
