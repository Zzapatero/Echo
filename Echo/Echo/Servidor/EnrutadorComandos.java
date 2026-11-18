package Servidor;

import ServidorComandos.*;
import ServidorInterfaces.IClienteConectado;
import ServidorInterfaces.IServicioMensajes;
import ServidorInterfaces.IServicioSalones;
import ServidorInterfaces.IServicioUsuarios;
import java.util.HashMap;
import java.util.Map;

public class EnrutadorComandos {
    private final Map<String, Comando> mapaComandos = new HashMap<>();
    private final IServicioUsuarios servicioUsuarios;

    /**
     * Componente encargado de interceptar las tramas crudas del protocolo de red,
     * identificar la palabra clave e invocar al comando correspondiente.
     */
    public EnrutadorComandos(IServicioUsuarios usuarios, IServicioSalones salones, IServicioMensajes mensajes) {
        this.servicioUsuarios = usuarios;
        mapaComandos.put("REG", new ComandoRegistro(usuarios));
        mapaComandos.put("LOGIN", new ComandoLogin(usuarios));
        mapaComandos.put("CHANGE_PASSWORD", new ComandoCambiarClave(usuarios));
        mapaComandos.put("JOIN_ROOM", new ComandoUnirSala(mensajes)); 
        mapaComandos.put("LEAVE_ROOM", new ComandoSalirSala());
        mapaComandos.put("MSG_ROOM", new ComandoMensajeSala(salones, mensajes));
        mapaComandos.put("LIST_USERS", new ComandoListaUsuarios(usuarios));
        mapaComandos.put("MSG_PRIV", new ComandoMensajePrivado());
        mapaComandos.put("ACK_PRIV", new ComandoAutenticado() {
            @Override
            protected void ejecutarAccion(IClienteConectado cliente, String[] partes) {
                if (partes.length >= 2) {
                    try {
                        ServidorChat.obtenerGestorMensajesPrivados().marcarEntregado(
                                partes[1], cliente.getNombreUsuario());
                    } catch (IllegalStateException e) {
                        GestorLogs.registrar("servidor.log", "No se pudo confirmar un mensaje privado: " + e.getMessage());
                    }
                }
            }
        });
        mapaComandos.put("LOGOUT", new ComandoLogout());
        mapaComandos.put("PING", new ComandoPing());
        mapaComandos.put("GET_HIST", new ComandoHistorial(mensajes));
    }

    /**
     * Trocea la cadena recibida, verifica el estado de mantenimiento del sistema
     * y ejecuta el comando solicitado si cumple las condiciones del protocolo.
     * @param mensajeCrudo Trama de texto separada por tuberías (|).
     * @param cliente Instancia del cliente conectado que invoca la acción.
     */
    public void procesar(String mensajeCrudo, IClienteConectado cliente) {
        String[] partes = mensajeCrudo.split("\\|", 3);
        String palabraClave = partes[0];
        

        if (cliente.isAutenticado() && servicioUsuarios.requiereCambioClave(cliente.getNombreUsuario())
                && !palabraClave.equals("CHANGE_PASSWORD") && !palabraClave.equals("LOGOUT")) {
            cliente.enviarMensaje("MSG_ERR|Debes actualizar tu clave antes de continuar.");
            return;
        }

        if (Servidor.ServidorChat.mantenimiento && !palabraClave.equals("LOGOUT")) {
            cliente.enviarMensaje("MSG_ERR|El servidor está en mantenimiento. Servicios suspendidos temporalmente.");
            return;
        }
        
        Comando comando = mapaComandos.get(palabraClave);
        if (comando != null) {
            comando.ejecutar(cliente, partes);
        } else {
            cliente.enviarMensaje("MSG_ERR|Protocolo no reconocido");
        }
    }
}
