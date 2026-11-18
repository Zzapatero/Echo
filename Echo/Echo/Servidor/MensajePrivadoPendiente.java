package Servidor;

/** Registro persistido de un mensaje privado pendiente. */
public class MensajePrivadoPendiente {
    private final String id;
    private final String fechaHora;
    private final String remitente;
    private final String destinatario;
    private final String contenido;

    public MensajePrivadoPendiente(String id, String fechaHora, String remitente,
            String destinatario, String contenido) {
        this.id = id;
        this.fechaHora = fechaHora;
        this.remitente = remitente;
        this.destinatario = destinatario;
        this.contenido = contenido;
    }

    public String getId() { return id; }
    public String getFechaHora() { return fechaHora; }
    public String getRemitente() { return remitente; }
    public String getDestinatario() { return destinatario; }
    public String getContenido() { return contenido; }
}
