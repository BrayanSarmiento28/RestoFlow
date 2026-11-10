package com.udec.restoflow.application.service;

import com.udec.restoflow.application.port.in.BuscarProductoExternoUseCase;
import com.udec.restoflow.application.port.out.CatalogoExternoPort;
import com.udec.restoflow.domain.exception.ProductoExternoNoEncontradoException;
import com.udec.restoflow.domain.model.ProductoExterno;

/**
 * Busca un producto por su código de barras en el catálogo externo.
 * <ol>
 *   <li>Valida el código (8, 12, 13 o 14 números) antes de llamar a internet.</li>
 *   <li>Consulta el catálogo por el puerto de salida.</li>
 *   <li>Si no existe responde 404; si el servicio está caído, 503 (lo lanza el adaptador).</li>
 * </ol>
 * No guarda nada: el usuario revisa los datos sugeridos y crea el insumo con el formulario de siempre.
 */
public class BuscarProductoExternoService implements BuscarProductoExternoUseCase {

    private final CatalogoExternoPort catalogo;

    public BuscarProductoExternoService(CatalogoExternoPort catalogo) {
        this.catalogo = catalogo;
    }

    @Override
    public ProductoExterno buscarPorCodigo(String codigoBarras) {
        String codigo = ProductoExterno.validarCodigo(codigoBarras);
        ProductoExterno producto = catalogo.buscarPorCodigo(codigo)
                .orElseThrow(() -> new ProductoExternoNoEncontradoException(codigo));
        Auditoria.productoExternoConsultado(codigo, producto.fuente());
        return producto;
    }
}
