package com.udec.restoflow.domain.model;

import com.udec.restoflow.domain.exception.DomainException;

import java.util.Locale;
import java.util.regex.Matcher;
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
    /** Número (con punto o coma decimal) seguido de la unidad: "1 kg", "1,5L", "250 ml". */
    private static final Pattern CANTIDAD = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s*(kg|kilo|g|gr|grs|gramos?|ml|cc|l|lt|litros?)\\b");
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
        Matcher m = CANTIDAD.matcher(cantidad.toLowerCase(Locale.ROOT));
        if (!m.find()) {
            return UnidadMedida.UNIDAD;
        }
        String unidad = m.group(2);
        if (unidad.startsWith("k")) {
            return UnidadMedida.KILOGRAMO;
        }
        if (unidad.startsWith("g")) {
            return UnidadMedida.GRAMO;
        }
        if (unidad.equals("ml") || unidad.equals("cc")) {
            return UnidadMedida.MILILITRO;
        }
        return UnidadMedida.LITRO;
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
