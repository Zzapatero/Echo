package ServidorInterfaces;

import Servidor.MensajePrivadoPendiente;
import java.util.List;

/** Persistencia de mensajes privados pendientes de entrega. */
public interface IServicioMensajesPrivados {
    MensajePrivadoPendiente guardarPendiente(String remitente, String destinatario, String contenido);
    List<MensajePrivadoPendiente> obtenerPendientes(String destinatario);
    void marcarEntregado(String id, String destinatario);
}
