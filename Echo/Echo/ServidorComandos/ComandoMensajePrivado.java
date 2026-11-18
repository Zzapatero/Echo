package ServidorComandos;

import Servidor.ServidorChat;
import ServidorInterfaces.IClienteConectado;

public class ComandoMensajePrivado extends ComandoAutenticado {
    @Override
    protected void ejecutarAccion(IClienteConectado cliente, String[] partes) {
        if (ServidorChat.mantenimiento) {
            cliente.enviarMensaje("MSG_ERR|El servidor está en mantenimiento.");
            return;
        }
        if (partes.length >= 3) {
            String destino = partes[1];
            String texto = partes[2];

            if (texto.trim().isEmpty() || texto.length() > 4000) {
                cliente.enviarMensaje("PRIV_ERR|El mensaje está vacío o supera el límite permitido");
                return;
            }

            if (!ServidorChat.obtenerGestorUsuarios().existeUsuario(destino)) {
                cliente.enviarMensaje("PRIV_ERR|usuario_no_encontrado");
                return;
            }
            if (destino.equals(cliente.getNombreUsuario())) {
                cliente.enviarMensaje("PRIV_ERR|no_puedes_escribirte_a_ti_mismo");
                return;
            }

            try {
                Servidor.MensajePrivadoPendiente mensaje = ServidorChat.obtenerGestorMensajesPrivados()
                        .guardarPendiente(cliente.getNombreUsuario(), destino, texto);
                boolean enviado = ServidorChat.enviarPrivadoDirecto(mensaje);
                cliente.enviarMensaje((enviado ? "PRIV_SENT|" : "PRIV_QUEUED|") + destino);
            } catch (IllegalStateException e) {
                cliente.enviarMensaje("PRIV_ERR|no_se_pudo_guardar_el_mensaje");
            }
        }
    }
}
