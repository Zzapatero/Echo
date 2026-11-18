package Cliente;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Abstracción de red del lado del cliente encargada de la comunicación por sockets,
 * envío de comandos básicos, escucha asíncrona del flujo de entrada y sistema de latidos (Heartbeat).
 */
public class ConexionServidor {
    private Socket socket;
    private PrintWriter salida;
    private BufferedReader entrada;

    /**
     * Establece la conexión de red por socket con la máquina remota y levanta los flujos de texto.
     * @param ip Dirección de red del servidor (ej. 127.0.0.1).
     * @param puerto Número de puerto de escucha (ej. 5000).
     * @throws IOException Si ocurre un fallo físico en el intento de conexión.
     */
    public void conectar(String ip, int puerto) throws IOException {
        this.socket = new Socket(ip, puerto);
        this.salida = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        this.entrada = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
    }

    /**
     * Escribe de forma inmediata un comando o protocolo de texto hacia el flujo de salida de red.
     * @param comando Trama de texto plano para enviar al servidor.
     */
    public synchronized void enviarComando(String comando) {
        if (salida != null && socket != null && !socket.isClosed()) {
            salida.println(comando);
            if (salida.checkError()) {
                System.out.println("No se pudo enviar el comando al servidor.");
                detenerSistemaHeartbeat();
            }
        }
    }

    /**
     * Arranca un hilo secundario infinito encargado de leer las respuestas del servidor
     * delegando cada línea recibida a una rutina funcional externa de la vista.
     * @param alRecibirMensaje Consumidor funcional encargado de procesar la trama recibida en la GUI.
     */
    public void escucharServidor(java.util.function.Consumer<String> alRecibirMensaje) {
        Thread hiloEscucha = new Thread(() -> {
            try {
                String linea;
                while ((linea = entrada.readLine()) != null) {
                    alRecibirMensaje.accept(linea);
                }
            } catch (IOException e) {
                if (socket != null && !socket.isClosed()) {
                    System.out.println("Conexión perdida con el servidor: " + e.getMessage());
                }
            } finally {
                detenerSistemaHeartbeat();
                alRecibirMensaje.accept("CONNECTION_CLOSED|");
            }
        }, "Echo-TCP-Listener");
        hiloEscucha.setDaemon(true);
        hiloEscucha.start();
    }

    public void cerrar() {
        detenerSistemaHeartbeat();
        try {
            if (socket != null && !socket.isClosed()) socket.close();
        } catch (IOException e) {
            System.out.println("No se pudo cerrar la conexión: " + e.getMessage());
        }
    }

    private Thread hiloHeartbeat;
    private volatile boolean ejecutarHeartbeat = false;
    private static final int TIEMPO_LATIDO_MS = 60000; 

    /**
     * Levanta de forma asíncrona y en segundo plano un temporizador demonio que inyecta tramas PING
     * de control de forma periódica cada 60 segundos para evitar la inactividad del socket.
     */
    public void iniciarSistemaHeartbeat() {
        detenerSistemaHeartbeat();
        ejecutarHeartbeat = true;
        
        Runnable tareaLatido = new Runnable() {
            @Override
            public void run() {
                try {
                    while (ejecutarHeartbeat && !socket.isClosed()) {
                        if (salida != null) {
                            salida.println("PING");
                            salida.flush();
                        }
                        Thread.sleep(TIEMPO_LATIDO_MS);
                    }
                } catch (InterruptedException e) {
                    // Hilo interrumpido pacíficamente
                } catch (Exception e) {
                    detenerSistemaHeartbeat();
                }
            }
        };

        hiloHeartbeat = new Thread(tareaLatido, "Hilo-Heartbeat-Cliente");
        hiloHeartbeat.setDaemon(true);
        hiloHeartbeat.start();
    }

    /**
     * Interrumpe el hilo del temporizador y detiene de forma segura el envío de señales de control.
     */
    public void detenerSistemaHeartbeat() {
        ejecutarHeartbeat = false;
        if (hiloHeartbeat != null && hiloHeartbeat.isAlive()) {
            hiloHeartbeat.interrupt();
        }
    }
}
