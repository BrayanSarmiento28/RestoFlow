package com.udec.restoflow.domain.model;

import com.udec.restoflow.domain.exception.DomainException;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Producto de un catálogo externo (Open Food Facts), encontrado por su código de barras.
 * Sirve para registrar un insumo sin escribir todo a mano: RestoFlow sugiere el nombre y la unidad de medida.
 *
 * @param cantidad tal como viene en el empaque, por ejemplo "1 kg", "500 g", "1,5 L" o "30 unidades"
 */
public record ProductoExterno(String codigoBarras, String nombre, String marca, String cantidad, String imagenUrl,
                              String fuente) {

    /** EAN-8, UPC-A (12), EAN-13 o GTIN-14: solo números. */
    private static final Pattern CODIGO_VALIDO = Pattern.compile("\\d{8}|\\d{12,14}");
    /** Palabras de unidad que se reconocen después del número en el empaque ("1 kg", "500g", "1,5 L"). */
    private static final Map<String, UnidadMedida> UNIDADES_DEL_EMPAQUE = Map.ofEntries(
            Map.entry("kg", UnidadMedida.KILOGRAMO), Map.entry("kilo", UnidadMedida.KILOGRAMO),
            Map.entry("g", UnidadMedida.GRAMO), Map.entry("gr", UnidadMedida.GRAMO), Map.entry("grs", UnidadMedida.GRAMO),
            Map.entry("gramo", UnidadMedida.GRAMO), Map.entry("gramos", UnidadMedida.GRAMO),
            Map.entry("ml", UnidadMedida.MILILITRO), Map.entry("cc", UnidadMedida.MILILITRO),
            Map.entry("l", UnidadMedida.LITRO), Map.entry("lt", UnidadMedida.LITRO),
            Map.entry("litro", UnidadMedida.LITRO), Map.entry("litros", UnidadMedida.LITRO));
    private static final int LARGO_MAXIMO_NOMBRE = 100;

    /** Quita espacios y guiones; si no es un código de barras válido lanza un error de negocio (400). */
    public static String validarCodigo(String codigo) {
        String limpio = codigo == null ? "" : codigo.replaceAll("[\\s-]", "");
        if (!CODIGO_VALIDO.matcher(limpio).matches()) {
            throw new DomainException("El código de barras debe tener 8, 12, 13 o 14 números");
        }
        return limpio;
    }

    /** Unidad para el inventario según lo que dice el empaque. Sin dato reconocible: UNIDAD. */
    public UnidadMedida unidadSugerida() {
        if (cantidad == null) {
            return UnidadMedida.UNIDAD;
        }
        String texto = cantidad.toLowerCase(Locale.ROOT);
        int i = 0;
        while (i < texto.length()) {
            if (!Character.isDigit(texto.charAt(i))) {
                i++;
                continue;
            }
            int finNumero = finDelNumero(texto, i);
            UnidadMedida unidad = UNIDADES_DEL_EMPAQUE.get(palabraDespues(texto, finNumero));
            if (unidad != null) {
                return unidad;
            }
            i = finNumero;
        }
        return UnidadMedida.UNIDAD;
    }

    /*
     * La cantidad se lee recorriendo el texto una sola vez, sin expresiones regulares: así el tiempo siempre es
     * proporcional al largo del texto y no hay riesgo de lentitud con textos raros (regla java:S8786 de SonarQube).
     */

    /** Posición donde termina un número como "500" o "1,5" que empieza en {@code inicio}. */
    private static int finDelNumero(String texto, int inicio) {
        int i = saltarDigitos(texto, inicio);
        if (i + 1 < texto.length() && (texto.charAt(i) == '.' || texto.charAt(i) == ',')
                && Character.isDigit(texto.charAt(i + 1))) {
            i = saltarDigitos(texto, i + 1);
        }
        return i;
    }

    private static int saltarDigitos(String texto, int i) {
        while (i < texto.length() && Character.isDigit(texto.charAt(i))) {
            i++;
        }
        return i;
    }

    /** La palabra (solo letras) que sigue al número, saltando espacios; vacía si sigue otro carácter. */
    private static String palabraDespues(String texto, int desde) {
        int i = desde;
        while (i < texto.length() && Character.isWhitespace(texto.charAt(i))) {
            i++;
        }
        int inicio = i;
        while (i < texto.length() && Character.isLetter(texto.charAt(i))) {
            i++;
        }
        boolean palabraCompleta = i == texto.length()
                || (!Character.isLetterOrDigit(texto.charAt(i)) && texto.charAt(i) != '_');
        return palabraCompleta ? texto.substring(inicio, i) : "";
    }

    /** "Arroz blanco · Diana" (sin repetir la marca si ya está en el nombre), máximo 100 caracteres. */
    public String nombreSugerido() {
        String base = nombre == null || nombre.isBlank() ? "Producto " + codigoBarras : nombre.trim();
        if (marca != null && !marca.isBlank()) {
            String primeraMarca = marca.split(",")[0].trim();
            if (!base.toLowerCase(Locale.ROOT).contains(primeraMarca.toLowerCase(Locale.ROOT))) {
                base = base + " · " + primeraMarca;
            }
        }
        return base.length() > LARGO_MAXIMO_NOMBRE ? base.substring(0, LARGO_MAXIMO_NOMBRE).trim() : base;
    }
}
