package ServidorComandos;

import ServidorInterfaces.IClienteConectado;
import ServidorInterfaces.IServicioUsuarios;
import Servidor.ServidorChat;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;

public class ComandoListaUsuarios extends ComandoAutenticado {
    private final IServicioUsuarios servicioUsuarios;

    public ComandoListaUsuarios(IServicioUsuarios servicioUsuarios) {
        this.servicioUsuarios = servicioUsuarios;
    }

    @Override
    protected void ejecutarAccion(IClienteConectado cliente, String[] partes) {
        Set<String> conectados = new HashSet<>();
        synchronized (ServidorChat.clientesConectados) {
            for (IClienteConectado c : ServidorChat.clientesConectados) {
                if (c.isAutenticado()) {
                    conectados.add(c.getNombreUsuario());
                }
            }
        }

        cliente.enviarMensaje("USERS_BEGIN");
        for (String usuario : servicioUsuarios.listarUsuarios()) {
            String usuarioCodificado = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(usuario.getBytes(StandardCharsets.UTF_8));
            cliente.enviarMensaje("USER|" + usuarioCodificado + "|"
                    + (conectados.contains(usuario) ? "ONLINE" : "OFFLINE"));
        }
        cliente.enviarMensaje("USERS_END");
    }
}
