package ServidorInterfaces;

import java.util.Set;

/**
 * Contrato del servicio de seguridad encargado del alta y validación de usuarios.
 */
public interface IServicioUsuarios {
    boolean existeUsuario(String nombre);
    boolean validarCredenciales(String nombre, String clave);
    boolean registrarUsuario(String nombre, String clave);
    boolean requiereCambioClave(String nombre);
    boolean cambiarClave(String nombre, String clave);
    Set<String> listarUsuarios();
}
