package com.udec.restoflow.application.port.in;

import com.udec.restoflow.domain.model.ProductoExterno;

/** RF-05 · Al crear un insumo, buscar sus datos por código de barras en un catálogo externo (Open Food Facts). */
public interface BuscarProductoExternoUseCase {

    ProductoExterno buscarPorCodigo(String codigoBarras);
}
