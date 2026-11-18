package Pruebas;

import Servidor.GestorSalonesMemoria;
import org.junit.Test;
import static org.junit.Assert.*;

public class GestorSalonesMemoriaTest {

    @Test
    public void testRegistrarYObtenerMetricas() {
        GestorSalonesMemoria gestor = new GestorSalonesMemoria();
        gestor.registrarMensaje("Inteligencia Artificial");
        gestor.registrarMensaje("Inteligencia Artificial");
        gestor.registrarMensaje("Fútbol");

        assertEquals("El canal Inteligencia Artificial debería tener 2 mensajes", Integer.valueOf(2), gestor.obtenerMetricas().get("Inteligencia Artificial"));
        assertEquals("El canal Fútbol debería tener 1 mensaje", Integer.valueOf(1), gestor.obtenerMetricas().get("Fútbol"));
        assertNull("El canal General no debería tener mensajes", gestor.obtenerMetricas().get("General"));
    }
}
