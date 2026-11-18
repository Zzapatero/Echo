package ServidorComandos;

import Servidor.ServidorChat;
import ServidorInterfaces.IClienteConectado;
import ServidorInterfaces.IServicioUsuarios;

/** Permite completar el cambio obligatorio de una credencial heredada. */
public class ComandoCambiarClave extends ComandoAutenticado {
    private final IServicioUsuarios servicioUsuarios;

    public ComandoCambiarClave(IServicioUsuarios servicioUsuarios) {
        this.servicioUsuarios = servicioUsuarios;
    }

    @Override
    protected void ejecutarAccion(IClienteConectado cliente, String[] partes) {
        if (partes.length < 2) {
            cliente.enviarMensaje("MSG_ERR|Introduce una clave nueva de al menos 12 caracteres.");
            return;
        }
        if (!servicioUsuarios.requiereCambioClave(cliente.getNombreUsuario())) {
            cliente.enviarMensaje("MSG_ERR|La clave de esta cuenta ya está actualizada.");
            return;
        }
        if (servicioUsuarios.cambiarClave(cliente.getNombreUsuario(), partes[1])) {
            cliente.enviarMensaje("PASSWORD_UPDATED");
            ServidorChat.finalizarInicioSesion(cliente);
        } else {
            cliente.enviarMensaje("MSG_ERR|La clave debe tener al menos 12 caracteres y no contener comas ni saltos de línea.");
        }
    }
}
