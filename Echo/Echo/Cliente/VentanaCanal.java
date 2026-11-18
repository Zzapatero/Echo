package Cliente;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.JLabel;

/** Ventana visual para conversar en un canal público o privado de Echo. */
public class VentanaCanal extends JFrame {
    private final JTextArea areaChat = new JTextArea();
    private final JTextField campoMensaje = new JTextField();
    private final String nombreCanal;
    private final ConexionServidor conexion;
    private final Runnable alCerrar;
    private int mensajesHistorialCargados = 0;
    private JButton botonHistorial;

    public VentanaCanal(String nombreCanal, ConexionServidor conexion, Runnable alCerrar) {
        this.nombreCanal = nombreCanal;
        this.conexion = conexion;
        this.alCerrar = alCerrar;

        boolean privado = nombreCanal.startsWith("Priv_");
        String titulo = privado ? "Conversación con " + nombreCanal.substring(5) : nombreCanal;
        setTitle(titulo + " · Echo");
        setSize(680, 520);
        setMinimumSize(new Dimension(520, 400));
        setLocationByPlatform(true);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

        JPanel contenido = new JPanel(new BorderLayout(14, 14));
        contenido.setBackground(EchoTheme.FONDO);
        contenido.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        setContentPane(contenido);

        JPanel encabezado = EchoTheme.glassPanel();
        encabezado.setLayout(new BorderLayout(10, 4));
        encabezado.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));
        JLabel etiquetaTitulo = new JLabel(titulo);
        etiquetaTitulo.setForeground(EchoTheme.TEXTO);
        etiquetaTitulo.setFont(etiquetaTitulo.getFont().deriveFont(Font.BOLD, 19f));
        JLabel etiquetaTipo = new JLabel(privado ? "MENSAJE DIRECTO" : "CANAL DE ECHO");
        etiquetaTipo.setForeground(EchoTheme.TEXTO_SUAVE);
        etiquetaTipo.setFont(etiquetaTipo.getFont().deriveFont(Font.BOLD, 10f));
        encabezado.add(etiquetaTitulo, BorderLayout.CENTER);
        encabezado.add(etiquetaTipo, BorderLayout.EAST);

        if (!privado) {
            botonHistorial = EchoTheme.button("Cargar anteriores");
            botonHistorial.addActionListener(e -> cargarHistorial());
            encabezado.add(botonHistorial, BorderLayout.SOUTH);
        }
        contenido.add(encabezado, BorderLayout.NORTH);

        JPanel panelMensajes = EchoTheme.glassPanel();
        panelMensajes.setLayout(new BorderLayout());
        panelMensajes.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        areaChat.setEditable(false);
        areaChat.setLineWrap(true);
        areaChat.setWrapStyleWord(true);
        EchoTheme.styleArea(areaChat);
        JScrollPane scrollMensajes = new JScrollPane(areaChat);
        scrollMensajes.setBorder(null);
        scrollMensajes.getViewport().setBackground(new java.awt.Color(17, 23, 40));
        panelMensajes.add(scrollMensajes, BorderLayout.CENTER);
        contenido.add(panelMensajes, BorderLayout.CENTER);

        JPanel panelEnviar = EchoTheme.glassPanel();
        panelEnviar.setLayout(new BorderLayout(10, 0));
        panelEnviar.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        EchoTheme.styleField(campoMensaje);
        campoMensaje.setToolTipText("Escribe un mensaje");
        JButton botonEnviar = EchoTheme.button("Enviar");
        panelEnviar.add(campoMensaje, BorderLayout.CENTER);
        panelEnviar.add(botonEnviar, BorderLayout.EAST);
        contenido.add(panelEnviar, BorderLayout.SOUTH);

        botonEnviar.addActionListener(e -> enviarMensaje());
        campoMensaje.addActionListener(e -> enviarMensaje());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                VentanaCanal.this.alCerrar.run();
            }
        });
    }

    private void cargarHistorial() {
        if (mensajesHistorialCargados >= 150) {
            botonHistorial.setEnabled(false);
            return;
        }
        conexion.enviarComando("GET_HIST|" + nombreCanal + "|" + mensajesHistorialCargados);
        mensajesHistorialCargados += 50;
        if (mensajesHistorialCargados >= 150) botonHistorial.setEnabled(false);
    }

    private void enviarMensaje() {
        String texto = campoMensaje.getText().trim();
        if (texto.isEmpty()) return;

        if (nombreCanal.startsWith("Priv_")) {
            String destino = nombreCanal.substring(5);
            conexion.enviarComando("MSG_PRIV|" + destino + "|" + texto);
            mostrarMensaje("Tú: " + texto);
        } else {
            conexion.enviarComando("MSG_ROOM|" + nombreCanal + "|" + texto);
        }
        campoMensaje.setText("");
    }

    public void mostrarMensaje(String mensaje) {
        areaChat.append(mensaje + "\n");
        areaChat.setCaretPosition(areaChat.getDocument().getLength());
    }
}
