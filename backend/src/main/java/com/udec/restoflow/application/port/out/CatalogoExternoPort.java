package com.udec.restoflow.application.port.out;

import com.udec.restoflow.domain.model.ProductoExterno;

import java.util.Optional;

/**
 * Puerto de salida hacia un catálogo externo de productos por código de barras (hoy Open Food Facts).
 * La aplicación no sabe qué API se usa: para cambiarla basta con escribir otro adaptador.
 */
public interface CatalogoExternoPort {

    /**
     * @return el producto, o vacío si el catálogo no lo tiene
     * @throws com.udec.restoflow.domain.exception.ServicioExternoNoDisponibleException si el servicio no respondió
     */
    Optional<ProductoExterno> buscarPorCodigo(String codigoBarras);
}
