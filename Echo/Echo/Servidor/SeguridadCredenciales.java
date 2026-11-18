package Servidor;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** Hash de contraseñas con PBKDF2, sal aleatoria y comparación en tiempo constante. */
public final class SeguridadCredenciales {
    private static final String ALGORITMO = "PBKDF2WithHmacSHA256";
    private static final int ITERACIONES = 210000;
    private static final int BYTES_SAL = 16;
    private static final int BITS_CLAVE = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private SeguridadCredenciales() {}

    public static String hash(String clave, boolean cambioObligatorio) {
        if (clave == null) throw new IllegalArgumentException("La clave no puede ser nula.");
        byte[] sal = new byte[BYTES_SAL];
        ALEATORIO.nextBytes(sal);
        byte[] derivada = derivar(clave.toCharArray(), sal, ITERACIONES);
        return "PBKDF2$" + ITERACIONES + "$" + hexadecimal(sal) + "$" + hexadecimal(derivada)
                + (cambioObligatorio ? "$RESET" : "");
    }

    public static boolean verificar(String clave, String almacenada) {
        if (clave == null || almacenada == null || !esHash(almacenada)) return false;
        try {
            String[] partes = almacenada.split("\\$");
            int iteraciones = Integer.parseInt(partes[1]);
            if (iteraciones < 100000 || iteraciones > 1000000) return false;
            byte[] sal = desdeHexadecimal(partes[2]);
            byte[] esperada = desdeHexadecimal(partes[3]);
            byte[] calculada = derivar(clave.toCharArray(), sal, iteraciones);
            return MessageDigest.isEqual(esperada, calculada);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static boolean esHash(String almacenada) {
        if (almacenada == null) return false;
        String[] partes = almacenada.split("\\$");
        return partes.length >= 4 && "PBKDF2".equals(partes[0]);
    }

    public static boolean requiereCambio(String almacenada) {
        if (!esHash(almacenada)) return true;
        String[] partes = almacenada.split("\\$");
        return partes.length >= 5 && "RESET".equals(partes[4]);
    }

    private static byte[] derivar(char[] clave, byte[] sal, int iteraciones) {
        PBEKeySpec especificacion = new PBEKeySpec(clave, sal, iteraciones, BITS_CLAVE);
        try {
            SecretKeyFactory fabrica = SecretKeyFactory.getInstance(ALGORITMO);
            return fabrica.generateSecret(especificacion).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo calcular el hash de la clave.", e);
        } finally {
            especificacion.clearPassword();
            Arrays.fill(clave, '\0');
        }
    }

    private static String hexadecimal(byte[] bytes) {
        StringBuilder texto = new StringBuilder(bytes.length * 2);
        for (byte valor : bytes) texto.append(String.format("%02x", valor & 0xff));
        return texto.toString();
    }

    private static byte[] desdeHexadecimal(String texto) {
        if ((texto.length() & 1) != 0) throw new IllegalArgumentException("Hexadecimal inválido.");
        byte[] bytes = new byte[texto.length() / 2];
        for (int i = 0; i < bytes.length; i++) {
            int alto = Character.digit(texto.charAt(i * 2), 16);
            int bajo = Character.digit(texto.charAt(i * 2 + 1), 16);
            if (alto < 0 || bajo < 0) throw new IllegalArgumentException("Hexadecimal inválido.");
            bytes[i] = (byte) ((alto << 4) + bajo);
        }
        return bytes;
    }
}
