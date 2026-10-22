package com.udec.restoflow.domain.exception;

/** El catálogo externo no tiene ningún producto con ese código de barras (404). */
public class ProductoExternoNoEncontradoException extends DomainException {

    public ProductoExternoNoEncontradoException(String codigo) {
        super("No se encontró ningún producto con el código " + codigo + ". Puede registrar el insumo escribiendo los datos.");
    }
}
