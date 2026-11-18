package Cliente;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import javax.swing.BorderFactory;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/** Interfaz visual principal de Echo: acceso, canales, personas y actividad. */
public class VentanaChat extends JFrame {
    private static final String[] CANALES = {
        "General", "Fútbol", "Inteligencia Artificial", "Noticias", "Actualidad", "Tecnología"
    };

    private final ConcurrentHashMap<String, VentanaCanal> ventanasAbiertas = new ConcurrentHashMap<>();
    private final DefaultListModel<UsuarioVisual> usuariosRegistrados = new DefaultListModel<>();
    private final DefaultListModel<UsuarioVisual> modeloUsuarios = new DefaultListModel<>();
    private final JList<UsuarioVisual> listaUsuarios = new JList<>(modeloUsuarios);
    private final JTextArea areaActividad = new JTextArea();
    private final JTextField campoUsuario = new JTextField();
    private final JTextField campoBuscarPersonas = new JTextField();
    private final JPasswordField campoClave = new JPasswordField();
    private final JPasswordField campoClaveNueva = new JPasswordField();
    private final JPasswordField campoConfirmarClave = new JPasswordField();
    private final JLabel etiquetaEstado = new JLabel("Conectando…");
    private final JLabel etiquetaEstadoAcceso = new JLabel("Conecta para iniciar tu sesión.");
    private final JLabel etiquetaEstadoCambioClave = new JLabel(" ");
    private final JButton botonActualizarUsuarios = EchoTheme.button("Actualizar");
    private final JButton botonAbrirPrivado = EchoTheme.button("Abrir conversación");
    private final JButton botonCerrarSesion = EchoTheme.button("Cerrar sesión");
    private final JButton[] botonesCanales = new JButton[CANALES.length];
    private final CardLayout tarjetas = new CardLayout();
    private final JPanel panelTarjetas = new JPanel(tarjetas);

    private ConexionServidor conexion;
    private boolean conexionActiva;
    private boolean sesionActiva;
    private boolean cambioClavePendiente;
    private boolean cierreSolicitado;
    private String nombreUsuarioActual;

    public VentanaChat() {
        setTitle("Echo · Mensajería TCP");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1180, 760);
        setMinimumSize(new Dimension(960, 640));
        setLocationRelativeTo(null);

        JPanel contenido = new JPanel(new BorderLayout(16, 16));
        contenido.setBackground(EchoTheme.FONDO);
        contenido.setBorder(BorderFactory.createEmptyBorder(18, 18, 18, 18));
        setContentPane(contenido);
        contenido.add(crearEncabezado(), BorderLayout.NORTH);

        JPanel cuerpo = new JPanel(new BorderLayout(14, 0));
        cuerpo.setOpaque(false);
        cuerpo.add(crearNavegacion(), BorderLayout.WEST);
        cuerpo.add(crearPanelCentral(), BorderLayout.CENTER);
        cuerpo.add(crearPanelPersonas(), BorderLayout.EAST);
        contenido.add(cuerpo, BorderLayout.CENTER);

        botonCerrarSesion.setEnabled(false);
        botonCerrarSesion.addActionListener(e -> cerrarSesion());
        botonActualizarUsuarios.addActionListener(e -> solicitarUsuarios());
        botonAbrirPrivado.addActionListener(e -> abrirChatSeleccionado());
        campoBuscarPersonas.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) { actualizarFiltroUsuarios(); }
            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) { actualizarFiltroUsuarios(); }
            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) { actualizarFiltroUsuarios(); }
        });
        listaUsuarios.addListSelectionListener(e -> {
            UsuarioVisual usuario = listaUsuarios.getSelectedValue();
            botonAbrirPrivado.setEnabled(sesionActiva && !cambioClavePendiente && usuario != null);
        });
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (conexionActiva && conexion != null) {
                    if (sesionActiva) conexion.enviarComando("LOGOUT");
                    conexion.cerrar();
                }
            }
        });

        conectarAlServidor();
    }

    private JPanel crearEncabezado() {
        JPanel encabezado = EchoTheme.glassPanel();
        encabezado.setLayout(new BorderLayout(12, 0));
        encabezado.setBorder(BorderFactory.createEmptyBorder(14, 20, 14, 18));

        JPanel marca = new JPanel(new BorderLayout(0, 2));
        marca.setOpaque(false);
        JLabel titulo = new JLabel("Echo");
        titulo.setForeground(EchoTheme.TEXTO);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 25f));
        JLabel subtitulo = new JLabel("Mensajería en tiempo real · TCP");
        subtitulo.setForeground(EchoTheme.TEXTO_SUAVE);
        subtitulo.setFont(subtitulo.getFont().deriveFont(12f));
        marca.add(titulo, BorderLayout.NORTH);
        marca.add(subtitulo, BorderLayout.SOUTH);
        encabezado.add(marca, BorderLayout.WEST);

        JPanel estado = new JPanel(new BorderLayout(14, 0));
        estado.setOpaque(false);
        etiquetaEstado.setForeground(EchoTheme.VERDE);
        etiquetaEstado.setHorizontalAlignment(SwingConstants.RIGHT);
        estado.add(etiquetaEstado, BorderLayout.CENTER);
        estado.add(botonCerrarSesion, BorderLayout.EAST);
        encabezado.add(estado, BorderLayout.EAST);
        return encabezado;
    }

    private JPanel crearNavegacion() {
        JPanel navegacion = EchoTheme.glassPanel();
        navegacion.setLayout(new BorderLayout(0, 12));
        navegacion.setBorder(BorderFactory.createEmptyBorder(18, 14, 18, 14));
        navegacion.setPreferredSize(new Dimension(205, 0));

        JLabel titulo = new JLabel("CANALES");
        titulo.setForeground(EchoTheme.TEXTO_SUAVE);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 11f));
        navegacion.add(titulo, BorderLayout.NORTH);

        JPanel listaCanales = new JPanel(new GridLayout(0, 1, 0, 8));
        listaCanales.setOpaque(false);
        for (int i = 0; i < CANALES.length; i++) {
            final String canal = CANALES[i];
            JButton boton = EchoTheme.button(canal);
            boton.setHorizontalAlignment(SwingConstants.LEFT);
            boton.setEnabled(false);
            boton.addActionListener(e -> abrirVentana(canal));
            botonesCanales[i] = boton;
            listaCanales.add(boton);
        }
        navegacion.add(listaCanales, BorderLayout.CENTER);

        JLabel pie = new JLabel("Conexión TCP protegida por sesión");
        pie.setForeground(EchoTheme.TEXTO_SUAVE);
        pie.setFont(pie.getFont().deriveFont(10f));
        navegacion.add(pie, BorderLayout.SOUTH);
        return navegacion;
    }

    private JPanel crearPanelCentral() {
        JPanel central = EchoTheme.glassPanel();
        central.setLayout(new BorderLayout());
        central.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        panelTarjetas.setOpaque(false);
        panelTarjetas.add(crearPanelAcceso(), "acceso");
        panelTarjetas.add(crearPanelCambioClave(), "cambio-clave");
        panelTarjetas.add(crearPanelInicio(), "inicio");
        central.add(panelTarjetas, BorderLayout.CENTER);
        return central;
    }

    private JPanel crearPanelAcceso() {
        JPanel acceso = new JPanel(new BorderLayout(0, 22));
        acceso.setOpaque(false);
        JLabel titulo = new JLabel("Tu espacio para conversar", SwingConstants.CENTER);
        titulo.setForeground(EchoTheme.TEXTO);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 25f));
        acceso.add(titulo, BorderLayout.NORTH);

        JPanel formulario = EchoTheme.glassPanel();
        formulario.setLayout(new GridLayout(0, 1, 0, 10));
        formulario.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));
        JLabel nombreLabel = etiqueta("Nombre de usuario");
        EchoTheme.styleField(campoUsuario);
        campoUsuario.setToolTipText("Tu nombre de usuario");
        JLabel claveLabel = etiqueta("Clave de acceso");
        EchoTheme.styleField(campoClave);
        campoClave.setToolTipText("Tu clave de acceso");
        campoUsuario.addActionListener(e -> campoClave.requestFocusInWindow());
        campoClave.addActionListener(e -> iniciarSesion());
        formulario.add(nombreLabel);
        formulario.add(campoUsuario);
        formulario.add(claveLabel);
        formulario.add(campoClave);

        JPanel acciones = new JPanel(new GridLayout(1, 2, 10, 0));
        acciones.setOpaque(false);
        JButton botonLogin = EchoTheme.button("Iniciar sesión");
        JButton botonRegistro = EchoTheme.button("Crear cuenta");
        botonLogin.addActionListener(e -> iniciarSesion());
        botonRegistro.addActionListener(e -> registrarUsuario());
        acciones.add(botonLogin);
        acciones.add(botonRegistro);
        formulario.add(acciones);

        etiquetaEstadoAcceso.setForeground(EchoTheme.TEXTO_SUAVE);
        etiquetaEstadoAcceso.setHorizontalAlignment(SwingConstants.CENTER);
        formulario.add(etiquetaEstadoAcceso);
        acceso.add(formulario, BorderLayout.CENTER);

        JLabel nota = new JLabel("Crea una cuenta o inicia sesión para ver canales y personas.", SwingConstants.CENTER);
        nota.setForeground(EchoTheme.TEXTO_SUAVE);
        acceso.add(nota, BorderLayout.SOUTH);
        return acceso;
    }

    private JPanel crearPanelCambioClave() {
        JPanel panel = new JPanel(new BorderLayout(0, 22));
        panel.setOpaque(false);
        JLabel titulo = new JLabel("Actualiza tu clave", SwingConstants.CENTER);
        titulo.setForeground(EchoTheme.TEXTO);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 24f));
        panel.add(titulo, BorderLayout.NORTH);

        JPanel formulario = EchoTheme.glassPanel();
        formulario.setLayout(new GridLayout(0, 1, 0, 10));
        formulario.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));
        EchoTheme.styleField(campoClaveNueva);
        EchoTheme.styleField(campoConfirmarClave);
        campoClaveNueva.setToolTipText("Al menos 12 caracteres");
        campoConfirmarClave.setToolTipText("Repite la clave nueva");
        formulario.add(etiqueta("Nueva clave (mínimo 12 caracteres)"));
        formulario.add(campoClaveNueva);
        formulario.add(etiqueta("Confirmar clave"));
        formulario.add(campoConfirmarClave);
        JButton botonCambiar = EchoTheme.button("Guardar nueva clave");
        botonCambiar.addActionListener(e -> cambiarClave());
        formulario.add(botonCambiar);
        etiquetaEstadoCambioClave.setForeground(EchoTheme.TEXTO_SUAVE);
        etiquetaEstadoCambioClave.setHorizontalAlignment(SwingConstants.CENTER);
        formulario.add(etiquetaEstadoCambioClave);
        panel.add(formulario, BorderLayout.CENTER);
        return panel;
    }

    private JPanel crearPanelInicio() {
        JPanel inicio = new JPanel(new BorderLayout(0, 18));
        inicio.setOpaque(false);
        JPanel bienvenida = EchoTheme.glassPanel();
        bienvenida.setLayout(new BorderLayout(0, 8));
        bienvenida.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        JLabel titulo = new JLabel("Bienvenido a Echo");
        titulo.setForeground(EchoTheme.TEXTO);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 23f));
        JLabel texto = new JLabel("Elige un canal o abre una conversación desde la lista de personas.");
        texto.setForeground(EchoTheme.TEXTO_SUAVE);
        bienvenida.add(titulo, BorderLayout.NORTH);
        bienvenida.add(texto, BorderLayout.CENTER);
        inicio.add(bienvenida, BorderLayout.NORTH);

        JPanel actividad = EchoTheme.glassPanel();
        actividad.setLayout(new BorderLayout(0, 10));
        actividad.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JLabel actividadTitulo = new JLabel("Actividad reciente");
        actividadTitulo.setForeground(EchoTheme.TEXTO);
        actividadTitulo.setFont(actividadTitulo.getFont().deriveFont(Font.BOLD, 15f));
        actividad.add(actividadTitulo, BorderLayout.NORTH);
        areaActividad.setEditable(false);
        areaActividad.setLineWrap(true);
        areaActividad.setWrapStyleWord(true);
        EchoTheme.styleArea(areaActividad);
        JScrollPane scrollActividad = new JScrollPane(areaActividad);
        scrollActividad.setBorder(null);
        scrollActividad.getViewport().setBackground(new Color(17, 23, 40));
        actividad.add(scrollActividad, BorderLayout.CENTER);
        inicio.add(actividad, BorderLayout.CENTER);
        return inicio;
    }

    private JPanel crearPanelPersonas() {
        JPanel personas = EchoTheme.glassPanel();
        personas.setLayout(new BorderLayout(8, 10));
        personas.setBorder(BorderFactory.createEmptyBorder(18, 14, 18, 14));
        personas.setPreferredSize(new Dimension(245, 0));

        JPanel encabezado = new JPanel(new BorderLayout(0, 8));
        encabezado.setOpaque(false);
        JPanel titulos = new JPanel(new BorderLayout(0, 4));
        titulos.setOpaque(false);
        JLabel titulo = new JLabel("PERSONAS");
        titulo.setForeground(EchoTheme.TEXTO_SUAVE);
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 11f));
        JLabel ayuda = new JLabel("Registradas y disponibles");
        ayuda.setForeground(EchoTheme.TEXTO_SUAVE);
        ayuda.setFont(ayuda.getFont().deriveFont(11f));
        titulos.add(titulo, BorderLayout.NORTH);
        titulos.add(ayuda, BorderLayout.SOUTH);
        encabezado.add(titulos, BorderLayout.NORTH);
        EchoTheme.styleField(campoBuscarPersonas);
        campoBuscarPersonas.setToolTipText("Buscar una persona");
        campoBuscarPersonas.putClientProperty("JTextField.placeholderText", "Buscar persona…");
        encabezado.add(campoBuscarPersonas, BorderLayout.SOUTH);
        personas.add(encabezado, BorderLayout.NORTH);

        listaUsuarios.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        listaUsuarios.setBackground(new Color(17, 23, 40));
        listaUsuarios.setForeground(EchoTheme.TEXTO);
        listaUsuarios.setSelectionBackground(new Color(65, 76, 140));
        listaUsuarios.setSelectionForeground(EchoTheme.TEXTO);
        listaUsuarios.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> lista, Object valor, int indice,
                    boolean seleccionado, boolean enfocado) {
                super.getListCellRendererComponent(lista, valor, indice, seleccionado, enfocado);
                UsuarioVisual usuario = (UsuarioVisual) valor;
                setText((usuario.enLinea ? "●  " : "○  ") + usuario.nombre);
                setForeground(seleccionado ? EchoTheme.TEXTO : usuario.enLinea ? EchoTheme.VERDE : EchoTheme.TEXTO_SUAVE);
                setBackground(seleccionado ? new Color(65, 76, 140) : new Color(17, 23, 40));
                setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
                return this;
            }
        });
        JScrollPane scrollUsuarios = new JScrollPane(listaUsuarios);
        scrollUsuarios.setBorder(null);
        scrollUsuarios.getViewport().setBackground(new Color(17, 23, 40));
        personas.add(scrollUsuarios, BorderLayout.CENTER);

        JPanel acciones = new JPanel(new GridLayout(0, 1, 0, 8));
        acciones.setOpaque(false);
        botonAbrirPrivado.setEnabled(false);
        acciones.add(botonAbrirPrivado);
        acciones.add(botonActualizarUsuarios);
        personas.add(acciones, BorderLayout.SOUTH);
        return personas;
    }

    private JLabel etiqueta(String texto) {
        JLabel label = new JLabel(texto);
        label.setForeground(EchoTheme.TEXTO_SUAVE);
        return label;
    }

    private void iniciarSesion() {
        String usuario = campoUsuario.getText().trim();
        String clave = new String(campoClave.getPassword());
        if (!validarNombre(usuario) || clave.isEmpty()) {
            mostrarEstadoAcceso("Introduce un nombre válido y tu clave.", true);
            return;
        }
        nombreUsuarioActual = usuario;
        enviarComando("LOGIN|" + usuario + "|" + clave);
        campoClave.setText("");
        mostrarEstadoAcceso("Comprobando credenciales…", false);
    }

    private void registrarUsuario() {
        String usuario = campoUsuario.getText().trim();
        String clave = new String(campoClave.getPassword());
        if (!validarNombre(usuario)) {
            mostrarEstadoAcceso("El nombre no puede estar vacío ni contener | , o saltos de línea.", true);
            return;
        }
        if (!claveNuevaValida(clave)) {
            mostrarEstadoAcceso("La clave debe tener al menos 12 caracteres y no contener comas ni saltos de línea.", true);
            return;
        }
        enviarComando("REG|" + usuario + "|" + clave);
        mostrarEstadoAcceso("Creando tu cuenta…", false);
    }

    private void cambiarClave() {
        String nueva = new String(campoClaveNueva.getPassword());
        String confirmar = new String(campoConfirmarClave.getPassword());
        if (!nueva.equals(confirmar)) {
            etiquetaEstadoCambioClave.setText("Las claves no coinciden.");
            etiquetaEstadoCambioClave.setForeground(new Color(255, 151, 151));
            return;
        }
        if (!claveNuevaValida(nueva)) {
            etiquetaEstadoCambioClave.setText("Usa al menos 12 caracteres, sin comas ni saltos de línea.");
            etiquetaEstadoCambioClave.setForeground(new Color(255, 151, 151));
            return;
        }
        enviarComando("CHANGE_PASSWORD|" + nueva);
        campoClaveNueva.setText("");
        campoConfirmarClave.setText("");
        etiquetaEstadoCambioClave.setText("Actualizando la clave…");
        etiquetaEstadoCambioClave.setForeground(EchoTheme.TEXTO_SUAVE);
    }

    private boolean claveNuevaValida(String clave) {
        return clave != null && clave.length() >= 12 && clave.indexOf('|') < 0
                && clave.indexOf(',') < 0 && clave.indexOf('\n') < 0 && clave.indexOf('\r') < 0;
    }

    private boolean validarNombre(String usuario) {
        return !usuario.isEmpty() && usuario.indexOf('|') < 0 && usuario.indexOf(',') < 0
                && usuario.indexOf('\n') < 0 && usuario.indexOf('\r') < 0;
    }

    private void cerrarSesion() {
        if (!sesionActiva) return;
        cierreSolicitado = true;
        enviarComando("LOGOUT");
        etiquetaEstado.setText("Cerrando sesión…");
    }

    private void conectarAlServidor() {
        try {
            conexion = new ConexionServidor();
            conexion.conectar("127.0.0.1", 5000);
            conexionActiva = true;
            etiquetaEstado.setText("Conectado al servidor TCP");
            etiquetaEstado.setForeground(EchoTheme.VERDE);
            conexion.escucharServidor(mensaje -> SwingUtilities.invokeLater(() -> procesarMensaje(mensaje)));
        } catch (Exception e) {
            conexionActiva = false;
            etiquetaEstado.setText("Servidor no disponible");
            etiquetaEstado.setForeground(new Color(255, 151, 151));
            mostrarEstadoAcceso("No se pudo conectar. Vuelve a intentarlo al iniciar sesión.", true);
        }
    }

    private void enviarComando(String comando) {
        if (!conexionActiva) conectarAlServidor();
        if (conexionActiva) conexion.enviarComando(comando);
    }

    private void procesarMensaje(String mensajeCrudo) {
        String[] partes = mensajeCrudo.split("\\|", 5);
        String comando = partes[0];
        switch (comando) {
            case "LOGIN_OK":
                sesionActiva = true;
                cierreSolicitado = false;
                conexion.iniciarSistemaHeartbeat();
                etiquetaEstado.setText("En línea · " + nombreUsuarioActual);
                etiquetaEstado.setForeground(EchoTheme.VERDE);
                botonCerrarSesion.setEnabled(true);
                if (partes.length >= 2 && "RESET_REQUIRED".equals(partes[1])) {
                    cambioClavePendiente = true;
                    etiquetaEstadoCambioClave.setText("Por seguridad, elige una clave nueva para continuar.");
                    etiquetaEstadoCambioClave.setForeground(EchoTheme.TEXTO_SUAVE);
                    tarjetas.show(panelTarjetas, "cambio-clave");
                } else {
                    activarSesionVisual();
                }
                break;
            case "PASSWORD_UPDATED":
                cambioClavePendiente = false;
                activarSesionVisual();
                mostrarEstadoAcceso("Clave actualizada correctamente.", false);
                registrarActividad("La clave de acceso se actualizó correctamente.");
                break;
            case "LOGIN_ERR":
                mostrarEstadoAcceso("No se pudo iniciar sesión. Revisa el nombre y la clave.", true);
                break;
            case "REG_OK":
                JOptionPane.showMessageDialog(this,
                        "Cuenta creada. Ya puedes iniciar sesión con la clave que elegiste.",
                        "Cuenta creada", JOptionPane.INFORMATION_MESSAGE);
                mostrarEstadoAcceso("Cuenta creada. Inicia sesión para continuar.", false);
                break;
            case "REG_ERR":
                mostrarEstadoAcceso(partes.length >= 2 ? partes[1].replace('_', ' ') : "No se pudo crear la cuenta.", true);
                break;
            case "USERS_BEGIN":
                usuariosRegistrados.clear();
                modeloUsuarios.clear();
                break;
            case "USER":
                if (partes.length >= 3) {
                    try {
                        String nombre = new String(Base64.getUrlDecoder().decode(partes[1]), StandardCharsets.UTF_8);
                        if (!nombre.equals(nombreUsuarioActual)) {
                            usuariosRegistrados.addElement(new UsuarioVisual(nombre, "ONLINE".equals(partes[2])));
                            actualizarFiltroUsuarios();
                        }
                    } catch (IllegalArgumentException e) {
                        registrarActividad("No se pudo leer una entrada del directorio.");
                    }
                }
                break;
            case "USERS_END":
                registrarActividad("Directorio actualizado: " + usuariosRegistrados.size() + " cuenta(s).");
                break;
            case "ROOM_BROADCAST":
                if (partes.length >= 5) {
                    mostrarEnCanal(partes[1], "[" + partes[3] + "] " + partes[2] + ": " + partes[4]);
                }
                break;
            case "HIST_DATA":
                if (partes.length >= 5) {
                    mostrarEnCanal(partes[1], "[HISTORIAL] [" + partes[3] + "] " + partes[2] + ": " + partes[4]);
                }
                break;
            case "RECV_PRIV":
                if (partes.length >= 5) {
                    try {
                        String contenido = new String(Base64.getDecoder().decode(partes[4]), StandardCharsets.UTF_8);
                        abrirVentana("Priv_" + partes[2]);
                        mostrarEnCanal("Priv_" + partes[2], "[" + partes[3] + "] " + partes[2] + ": " + contenido);
                        enviarComando("ACK_PRIV|" + partes[1]);
                    } catch (IllegalArgumentException e) {
                        registrarActividad("No se pudo leer un mensaje privado recibido.");
                    }
                }
                break;
            case "NOTIFY_JOIN":
                if (partes.length >= 2) registrarActividad(partes[1] + " se ha conectado.");
                solicitarUsuarios();
                break;
            case "NOTIFY_LEAVE":
                if (partes.length >= 2) registrarActividad(partes[1] + " se ha desconectado.");
                solicitarUsuarios();
                break;
            case "PRIV_ERR":
                registrarActividad("No se pudo enviar el mensaje privado: "
                        + (partes.length >= 2 ? partes[1].replace('_', ' ') : "error desconocido"));
                break;
            case "PRIV_SENT":
                registrarActividad("Mensaje privado enviado a " + (partes.length >= 2 ? partes[1] : "la persona") + ".");
                break;
            case "PRIV_QUEUED":
                registrarActividad("Mensaje guardado para entregar cuando "
                        + (partes.length >= 2 ? partes[1] : "la persona") + " se conecte.");
                break;
            case "MSG_ERR":
                String error = partes.length >= 2 ? partes[1] : "El servidor rechazó la acción.";
                if (cambioClavePendiente) {
                    etiquetaEstadoCambioClave.setText(error);
                    etiquetaEstadoCambioClave.setForeground(new Color(255, 151, 151));
                } else {
                    registrarActividad(error);
                }
                break;
            case "MAINTENANCE":
                if (partes.length >= 2 && "ON".equals(partes[1])) {
                    registrarActividad("El servidor está en mantenimiento. Cerrando sesión.");
                    cerrarSesion();
                } else {
                    registrarActividad("El mantenimiento del servidor ha finalizado.");
                }
                break;
            case "CONNECTION_CLOSED":
                conexionActiva = false;
                if (conexion != null) conexion.cerrar();
                terminarSesionLocal();
                if (cierreSolicitado) {
                    mostrarEstadoAcceso("Sesión cerrada. Puedes volver a conectarte.", false);
                    cierreSolicitado = false;
                } else {
                    mostrarEstadoAcceso("Se perdió la conexión. Inicia sesión para volver a conectar.", true);
                }
                etiquetaEstado.setText("Desconectado");
                etiquetaEstado.setForeground(new Color(255, 151, 151));
                break;
            default:
                registrarActividad(mensajeCrudo);
                break;
        }
    }

    private void activarSesionVisual() {
        cambioClavePendiente = false;
        mostrarEstadoAcceso("Sesión iniciada.", false);
        for (JButton boton : botonesCanales) boton.setEnabled(true);
        botonActualizarUsuarios.setEnabled(true);
        botonAbrirPrivado.setEnabled(listaUsuarios.getSelectedValue() != null);
        tarjetas.show(panelTarjetas, "inicio");
        registrarActividad("Has iniciado sesión en Echo.");
        solicitarUsuarios();
    }

    private void terminarSesionLocal() {
        sesionActiva = false;
        cambioClavePendiente = false;
        botonCerrarSesion.setEnabled(false);
        botonActualizarUsuarios.setEnabled(false);
        botonAbrirPrivado.setEnabled(false);
        for (JButton boton : botonesCanales) boton.setEnabled(false);
        modeloUsuarios.clear();
        usuariosRegistrados.clear();
        for (VentanaCanal ventana : ventanasAbiertas.values()) ventana.dispose();
        ventanasAbiertas.clear();
        tarjetas.show(panelTarjetas, "acceso");
        nombreUsuarioActual = null;
    }

    private void solicitarUsuarios() {
        if (sesionActiva) enviarComando("LIST_USERS");
    }

    private void abrirChatSeleccionado() {
        UsuarioVisual usuario = listaUsuarios.getSelectedValue();
        if (sesionActiva && !cambioClavePendiente && usuario != null) {
            abrirVentana("Priv_" + usuario.nombre);
        }
    }

    private void abrirVentana(String nombreCanal) {
        if (!sesionActiva) return;
        VentanaCanal existente = ventanasAbiertas.get(nombreCanal);
        if (existente != null && existente.isDisplayable()) {
            existente.toFront();
            return;
        }

        VentanaCanal nuevaVentana = new VentanaCanal(nombreCanal, conexion, () -> {
            ventanasAbiertas.remove(nombreCanal);
            if (sesionActiva && !nombreCanal.startsWith("Priv_")) {
                enviarComando("LEAVE_ROOM|" + nombreCanal);
            }
        });
        ventanasAbiertas.put(nombreCanal, nuevaVentana);
        nuevaVentana.setLocationRelativeTo(this);
        nuevaVentana.setVisible(true);
        if (!nombreCanal.startsWith("Priv_")) enviarComando("JOIN_ROOM|" + nombreCanal);
    }

    private void mostrarEnCanal(String canal, String mensaje) {
        VentanaCanal ventana = ventanasAbiertas.get(canal);
        if (ventana != null) ventana.mostrarMensaje(mensaje);
    }

    private void actualizarFiltroUsuarios() {
        if (modeloUsuarios == null) return;
        String filtro = campoBuscarPersonas.getText().trim().toLowerCase(Locale.ROOT);
        UsuarioVisual seleccionado = listaUsuarios.getSelectedValue();
        modeloUsuarios.clear();
        for (int i = 0; i < usuariosRegistrados.size(); i++) {
            UsuarioVisual usuario = usuariosRegistrados.get(i);
            if (usuario.nombre.toLowerCase(Locale.ROOT).contains(filtro)) {
                modeloUsuarios.addElement(usuario);
            }
        }
        if (seleccionado != null) listaUsuarios.setSelectedValue(seleccionado, true);
    }

    private void mostrarEstadoAcceso(String mensaje, boolean error) {
        etiquetaEstadoAcceso.setText(mensaje);
        etiquetaEstadoAcceso.setForeground(error ? new Color(255, 151, 151) : EchoTheme.VERDE);
    }

    private void registrarActividad(String mensaje) {
        areaActividad.append("•  " + mensaje + "\n");
        areaActividad.setCaretPosition(areaActividad.getDocument().getLength());
    }

    private static class UsuarioVisual {
        private final String nombre;
        private final boolean enLinea;

        private UsuarioVisual(String nombre, boolean enLinea) {
            this.nombre = nombre;
            this.enLinea = enLinea;
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VentanaChat().setVisible(true));
    }
}
