package ServidorComandos;

import ServidorInterfaces.IClienteConectado;
import ServidorInterfaces.IServicioUsuarios;

public class ComandoRegistro implements Comando {
    private final IServicioUsuarios servicioUsuarios;

    public ComandoRegistro(IServicioUsuarios servicioUsuarios) {
        this.servicioUsuarios = servicioUsuarios;
    }

    @Override
    public void ejecutar(IClienteConectado cliente, String[] partes) {
        if (partes.length < 3) {
            cliente.enviarMensaje("REG_ERR|Formato incorrecto");
            return;
        }
        String nombre = partes[1].trim();
        String clave = partes[2];
        if (!nombreValido(nombre)) {
            cliente.enviarMensaje("REG_ERR|Nombre no válido");
            return;
        }
        if (clave.length() < 12 || clave.indexOf(',') >= 0 || clave.indexOf('|') >= 0
                || clave.indexOf('\n') >= 0 || clave.indexOf('\r') >= 0) {
            cliente.enviarMensaje("REG_ERR|La clave debe tener al menos 12 caracteres");
            return;
        }
        if (servicioUsuarios.existeUsuario(nombre)) {
            cliente.enviarMensaje("REG_ERR|Usuario ya registrado");
        } else if (servicioUsuarios.registrarUsuario(nombre, clave)) {
            Servidor.GestorLogs.registrar("servidor.log", "Nuevo usuario registrado: " + nombre);
            cliente.enviarMensaje("REG_OK");
        } else {
            cliente.enviarMensaje("REG_ERR|No se pudo guardar la cuenta");
        }
    }

    private boolean nombreValido(String nombre) {
        return !nombre.isEmpty() && nombre.indexOf(',') < 0 && nombre.indexOf('|') < 0
                && nombre.indexOf('\n') < 0 && nombre.indexOf('\r') < 0;
    }
}
