package Servidor;

import Cliente.ManejadorClientes;
import ServidorInterfaces.IClienteConectado;
import ServidorInterfaces.IServicioMensajes;
import ServidorInterfaces.IServicioMensajesPrivados;
import ServidorInterfaces.IServicioSalones;
import ServidorInterfaces.IServicioUsuarios;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Servidor principal del chat que gestiona el ciclo de vida de las conexiones,
 * la difusión de mensajes por salas y el estado de mantenimiento del sistema.
 */
public class ServidorChat {
    private static final int PUERTO = 5000;
    public static final List<IClienteConectado> clientesConectados = Collections.synchronizedList(new ArrayList<>());
    private static final IServicioMensajes gestorMensajes = new GestorMensajesCSV();
    private static final IServicioMensajesPrivados gestorMensajesPrivados = new GestorMensajesPrivadosCSV();
    
    /**
     * Devuelve el servicio encargado de la persistencia e historial de mensajes.
     * @return Instancia del gestor de mensajes en formato CSV.
     */
    public static IServicioMensajes obtenerGestorMensajes() { return gestorMensajes; }
    public static IServicioMensajesPrivados obtenerGestorMensajesPrivados() { return gestorMensajesPrivados; }
    
    public static volatile boolean aceptarConexiones = true;
    public static volatile boolean mantenimiento = false;

    private static final IServicioUsuarios gestorUsuarios = (IServicioUsuarios) new GestorUsuariosCSV();
    private static final IServicioSalones gestorSalones = (IServicioSalones) new GestorSalonesMemoria();

    /**
     * Devuelve el servicio encargado del registro y autenticación de usuarios.
     * @return Instancia del gestor de usuarios en formato CSV.
     */
    public static IServicioUsuarios obtenerGestorUsuarios() { return gestorUsuarios; }
    /**
     * Devuelve el servicio encargado del control de métricas de los salones.
     * @return Instancia del gestor de salones en memoria.
     */
    public static IServicioSalones obtenerGestorSalones() { return gestorSalones; }

    /**
     * Difunde una trama de notificación a todos los usuarios autenticados excepto al emisor.
     * @param trama Cadena de texto con el protocolo de notificación (ej. NOTIFY_JOIN).
     * @param emisor Cliente conectado que origina la notificación.
     */
    public static void difundirNotificacion(String trama, IClienteConectado emisor) {
        synchronized (clientesConectados) {
            for (IClienteConectado cliente : clientesConectados) {
                if (cliente != emisor && cliente.isAutenticado()) {
                    cliente.enviarMensaje(trama);
                }
            }
        }
    }

    /**
     * Difunde un mensaje a todos los usuarios que tengan el salón activo en sus pantallas.
     * @param salon Nombre de la sala de chat de destino.
     * @param trama Cadena de texto formateada con el mensaje y metadatos.
     */
    public static void difundirPorSala(String salon, String trama) {
        synchronized (clientesConectados) {
            for (IClienteConectado cliente : clientesConectados) {
                if (cliente.isAutenticado() && cliente.getSalonesActivos().contains(salon)) {
                    cliente.enviarMensaje(trama);
                }
            }
        }
    }

    /**
     * Intenta enviar un mensaje privado directo a un usuario específico mediante socket en memoria viva.
     * @param remitente Nombre del usuario que envía el mensaje.
     * @param destino Nombre del usuario que debería recibir el mensaje.
     * @param msg Contenido textual del mensaje privado.
     * @return true si el usuario destino está conectado y autenticado, false en caso contrario.
     */
    public static boolean enviarPrivadoDirecto(MensajePrivadoPendiente mensaje) {
        synchronized (clientesConectados) {
            for (IClienteConectado c : clientesConectados) {
                if (c.isAutenticado() && c.getNombreUsuario().equals(mensaje.getDestinatario())) {
                    c.enviarMensaje(tramaPrivada(mensaje));
                    return true;
                }
            }
        }
        return false;
    }

    public static String tramaPrivada(MensajePrivadoPendiente mensaje) {
        String contenidoCodificado = java.util.Base64.getEncoder().encodeToString(
                mensaje.getContenido().getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return "RECV_PRIV|" + mensaje.getId() + "|" + mensaje.getRemitente() + "|"
                + mensaje.getFechaHora() + "|" + contenidoCodificado;
    }

    public static void finalizarInicioSesion(IClienteConectado cliente) {
        for (MensajePrivadoPendiente mensaje : gestorMensajesPrivados.obtenerPendientes(cliente.getNombreUsuario())) {
            cliente.enviarMensaje(tramaPrivada(mensaje));
        }
        difundirNotificacion("NOTIFY_JOIN|" + cliente.getNombreUsuario(), cliente);
    }

    /**
     * Calcula y muestra por la terminal del servidor las métricas en tiempo real:
     * usuarios online totales, usuarios activos por sala y mensajes acumulados por salón.
     */
    public static synchronized void imprimirMetricas() {
        System.out.println(obtenerResumenMetricas());
    }

    public static synchronized String obtenerResumenMetricas() {
        StringBuilder resumen = new StringBuilder("=== ECHO · ESTADÍSTICAS ===\n");
        int activos = 0;
        java.util.Map<String, Integer> salasActivas = new java.util.HashMap<>();
        
        synchronized (clientesConectados) {
            activos = clientesConectados.size();
            for (IClienteConectado c : clientesConectados) {
                for (String sala : c.getSalonesActivos()) {
                    salasActivas.put(sala, salasActivas.getOrDefault(sala, 0) + 1);
                }
            }
        }
        resumen.append("Usuarios conectados: ").append(activos).append('\n');
        resumen.append("Personas por canal:\n");
        String[] salonesSoportados = {"General", "Fútbol", "Inteligencia Artificial", "Noticias", "Actualidad", "Tecnología"};
        for (String s : salonesSoportados) {
            int cant = salasActivas.getOrDefault(s, 0);
            resumen.append(" · ").append(s).append(": ").append(cant).append(" en línea\n");
        }
        resumen.append("Mensajes por canal:\n");
        for (String s : salonesSoportados) {
            int cant = gestorSalones.obtenerMetricas().getOrDefault(s, 0);
            resumen.append(" · ").append(s).append(": ").append(cant).append('\n');
        }
        return resumen.toString();
    }
    /**
     * Difunde una trama de control o texto a absolutamente todos los clientes conectados en el socket.
     * @param trama Cadena de texto con el protocolo o mensaje para los clientes.
     */
    public static void difundirATodos(String trama) {
    synchronized (clientesConectados) {
        for (IClienteConectado cliente : clientesConectados) {
            cliente.enviarMensaje(trama);
        }
    }
}

    /**
     * Arranca la web local y acepta conexiones TCP en loopback.
     */
    public void iniciar() {
        try (ServerSocket serverSocket = new ServerSocket(PUERTO, 50,
                java.net.InetAddress.getByName("127.0.0.1"))) {
            new EchoWebServer().iniciar();
            System.out.println("Servidor Echo operativo en 127.0.0.1:" + PUERTO);
            GestorLogs.registrar("servidor.log", "SERVIDOR ECHO INICIADO EN 127.0.0.1:" + PUERTO);
            while (true) {
                Socket socketCliente = serverSocket.accept();
                if (!aceptarConexiones) {
                    PrintWriter salidaTemp = new PrintWriter(socketCliente.getOutputStream(), true);
                    salidaTemp.println("LOGIN_ERR|Credenciales_invalidas");
                    socketCliente.close();
                    continue;
                }
                ManejadorClientes manejador = new ManejadorClientes(socketCliente);
                clientesConectados.add(manejador);
                new Thread(manejador).start();
                imprimirMetricas();
            }
        } catch (IOException e) {
            System.out.println("Error en servidor: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        new ServidorChat().iniciar();
    }
}
