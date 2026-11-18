package Servidor;

import Cliente.EchoTheme;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/** Panel local para controlar el estado y consultar métricas del servidor Echo. */
public class VentanaAdministracion extends JFrame {
    private final JLabel estadoAcceso = new JLabel();
    private final JLabel estadoMantenimiento = new JLabel();
    private final JTextArea areaMetricas = new JTextArea();
    private final JButton botonAcceso = EchoTheme.button("");
    private final JButton botonMantenimiento = EchoTheme.button("");
    private Timer refrescoAutomatico;

    public VentanaAdministracion() {
        setTitle("Echo · Administración del servidor");
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setSize(760, 600);
        setMinimumSize(new Dimension(620, 480));
        setLocationByPlatform(true);

        JPanel contenido = new JPanel(new BorderLayout(14, 14));
        contenido.setBackground(EchoTheme.FONDO);
        contenido.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        setContentPane(contenido);

        JPanel encabezado = EchoTheme.glassPanel();
        encabezado.setLayout(new BorderLayout());
        encabezado.setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        JLabel titulo = new JLabel("Centro de administración");
        titulo.setForeground(EchoTheme.TEXTO);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 22f));
        JLabel subtitulo = new JLabel("Control local del servidor TCP de Echo");
        subtitulo.setForeground(EchoTheme.TEXTO_SUAVE);
        JPanel textos = new JPanel(new GridLayout(2, 1, 0, 4));
        textos.setOpaque(false);
        textos.add(titulo);
        textos.add(subtitulo);
        encabezado.add(textos, BorderLayout.CENTER);
        contenido.add(encabezado, BorderLayout.NORTH);

        JPanel controles = EchoTheme.glassPanel();
        controles.setLayout(new GridLayout(2, 2, 12, 10));
        controles.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        controles.add(etiqueta("Admisión de conexiones"));
        controles.add(estadoAcceso);
        controles.add(botonAcceso);
        controles.add(new JLabel(""));
        controles.add(etiqueta("Estado del servicio"));
        controles.add(estadoMantenimiento);
        controles.add(botonMantenimiento);
        controles.add(new JLabel(""));

        JPanel centro = new JPanel(new BorderLayout(0, 12));
        centro.setOpaque(false);
        centro.add(controles, BorderLayout.NORTH);

        JPanel panelMetricas = EchoTheme.glassPanel();
        panelMetricas.setLayout(new BorderLayout(0, 10));
        panelMetricas.setBorder(BorderFactory.createEmptyBorder(14, 16, 14, 16));
        JLabel tituloMetricas = etiqueta("Métricas en tiempo real");
        tituloMetricas.setFont(tituloMetricas.getFont().deriveFont(Font.BOLD, 14f));
        panelMetricas.add(tituloMetricas, BorderLayout.NORTH);
        areaMetricas.setEditable(false);
        EchoTheme.styleArea(areaMetricas);
        JScrollPane scroll = new JScrollPane(areaMetricas);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(new Color(17, 23, 40));
        panelMetricas.add(scroll, BorderLayout.CENTER);
        JButton botonActualizar = EchoTheme.button("Actualizar métricas");
        botonActualizar.addActionListener(e -> refrescarMetricas());
        panelMetricas.add(botonActualizar, BorderLayout.SOUTH);
        centro.add(panelMetricas, BorderLayout.CENTER);
        contenido.add(centro, BorderLayout.CENTER);

        botonAcceso.addActionListener(e -> {
            ServidorChat.aceptarConexiones = !ServidorChat.aceptarConexiones;
            actualizarEstados();
        });
        botonMantenimiento.addActionListener(e -> {
            ServidorChat.mantenimiento = !ServidorChat.mantenimiento;
            String estado = ServidorChat.mantenimiento ? "ON" : "OFF";
            if (ServidorChat.mantenimiento) {
                Servidor.GestorLogs.registrar("servidor.log", "Mantenimiento activado desde el panel de Echo.");
            }
            ServidorChat.difundirATodos("MAINTENANCE|" + estado);
            actualizarEstados();
        });

        actualizarEstados();
        refrescarMetricas();
        refrescoAutomatico = new Timer(2500, e -> refrescarMetricas());
        refrescoAutomatico.start();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                refrescoAutomatico.stop();
            }
        });
    }

    private JLabel etiqueta(String texto) {
        JLabel label = new JLabel(texto, SwingConstants.LEFT);
        label.setForeground(EchoTheme.TEXTO_SUAVE);
        return label;
    }

    private void actualizarEstados() {
        boolean acepta = ServidorChat.aceptarConexiones;
        estadoAcceso.setText(acepta ? "Abierto" : "Pausado");
        estadoAcceso.setForeground(acepta ? EchoTheme.VERDE : new Color(255, 180, 120));
        botonAcceso.setText(acepta ? "Pausar nuevas conexiones" : "Aceptar conexiones");

        boolean enMantenimiento = ServidorChat.mantenimiento;
        estadoMantenimiento.setText(enMantenimiento ? "Mantenimiento" : "Operativo");
        estadoMantenimiento.setForeground(enMantenimiento ? new Color(255, 180, 120) : EchoTheme.VERDE);
        botonMantenimiento.setText(enMantenimiento ? "Finalizar mantenimiento" : "Iniciar mantenimiento");
    }

    private void refrescarMetricas() {
        areaMetricas.setText(ServidorChat.obtenerResumenMetricas());
        areaMetricas.setCaretPosition(0);
    }
}
