package Cliente;

import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.Timer;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;

/** Colores y componentes ligeros del tema oscuro de Echo. */
public final class EchoTheme {
    public static final Color FONDO = new Color(12, 16, 29);
    public static final Color TEXTO = new Color(236, 241, 255);
    public static final Color TEXTO_SUAVE = new Color(151, 164, 194);
    public static final Color ACENTO = new Color(113, 123, 255);
    public static final Color VERDE = new Color(85, 222, 177);

    private EchoTheme() {}

    public static JPanel glassPanel() {
        return new GlassPanel();
    }

    public static JButton button(String texto) {
        return new GlassButton(texto);
    }

    public static void styleField(JTextField campo) {
        campo.setForeground(TEXTO);
        campo.setCaretColor(TEXTO);
        campo.setBackground(new Color(17, 23, 40));
        campo.setSelectionColor(new Color(77, 91, 170));
        campo.setBorder(new EmptyBorder(11, 13, 11, 13));
        campo.setFont(campo.getFont().deriveFont(14f));
    }

    public static void styleArea(JTextArea area) {
        area.setForeground(TEXTO);
        area.setCaretColor(TEXTO);
        area.setBackground(new Color(17, 23, 40));
        area.setSelectionColor(new Color(77, 91, 170));
        area.setFont(area.getFont().deriveFont(14f));
        area.setBorder(new EmptyBorder(12, 14, 12, 14));
    }

    private static class GlassPanel extends JPanel {
        private GlassPanel() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setPaint(new GradientPaint(0, 0, new Color(37, 47, 75, 235),
                    getWidth(), getHeight(), new Color(21, 28, 47, 238)));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
            g.setColor(new Color(174, 190, 255, 38));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 24, 24);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static class GlassButton extends JButton {
        private float hover;
        private float objetivoHover;
        private final Timer animacion;

        private GlassButton(String texto) {
            super(texto);
            setForeground(TEXTO);
            setFont(getFont().deriveFont(13f).deriveFont(java.awt.Font.BOLD));
            setFocusPainted(false);
            setContentAreaFilled(false);
            setBorder(new EmptyBorder(11, 15, 11, 15));
            setOpaque(false);
            setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
            animacion = new Timer(16, e -> {
                hover += (objetivoHover - hover) * 0.3f;
                if (Math.abs(objetivoHover - hover) < 0.02f) {
                    hover = objetivoHover;
                    ((Timer) e.getSource()).stop();
                }
                repaint();
            });
            getModel().addChangeListener(e -> {
                objetivoHover = getModel().isRollover() || getModel().isPressed() ? 1f : 0f;
                if (!animacion.isRunning()) animacion.start();
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            Color base = getModel().isPressed() ? new Color(79, 88, 192, 235) : new Color(79, 91, 177, 190);
            Color resaltado = new Color(ACENTO.getRed(), ACENTO.getGreen(), ACENTO.getBlue(), 235);
            Color inicio = mezclar(base, resaltado, hover);
            g.setPaint(new GradientPaint(0, 0, inicio, 0, getHeight(), new Color(55, 68, 132, 190)));
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 18, 18);
            g.setColor(new Color(199, 211, 255, 55));
            g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, 18, 18);
            g.dispose();
            super.paintComponent(graphics);
        }

        private Color mezclar(Color inicio, Color fin, float cantidad) {
            int rojo = (int) (inicio.getRed() + (fin.getRed() - inicio.getRed()) * cantidad);
            int verde = (int) (inicio.getGreen() + (fin.getGreen() - inicio.getGreen()) * cantidad);
            int azul = (int) (inicio.getBlue() + (fin.getBlue() - inicio.getBlue()) * cantidad);
            int alfa = (int) (inicio.getAlpha() + (fin.getAlpha() - inicio.getAlpha()) * cantidad);
            return new Color(rojo, verde, azul, alfa);
        }
    }
}
