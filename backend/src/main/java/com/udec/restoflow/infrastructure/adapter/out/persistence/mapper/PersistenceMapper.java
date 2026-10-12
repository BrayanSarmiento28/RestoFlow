package com.udec.restoflow.infrastructure.adapter.out.persistence.mapper;

import com.udec.restoflow.domain.model.Categoria;
import com.udec.restoflow.domain.model.CodigoRecuperacion;
import com.udec.restoflow.domain.model.DetallePedido;
import com.udec.restoflow.domain.model.Pedido;
import com.udec.restoflow.domain.model.Producto;
import com.udec.restoflow.domain.model.Usuario;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.CategoriaEntity;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.CodigoRecuperacionEntity;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.DetallePedidoEntity;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.PedidoEntity;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.ProductoEntity;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.UsuarioEntity;

/** Convierte entidades JPA en modelos de dominio. Así el dominio nunca ve anotaciones de base de datos. */
public final class PersistenceMapper {

    private PersistenceMapper() {
    }

    public static Usuario aDominio(UsuarioEntity e) {
        return new Usuario(e.getId(), e.getNombre(), e.getEmail(), e.getPasswordHash(), e.getRol(), e.isActivo(),
                e.getFechaCreacion());
    }

    public static CodigoRecuperacion aDominio(CodigoRecuperacionEntity e) {
        return new CodigoRecuperacion(e.getId(), e.getUsuario().getId(), e.getCodigoHash(), e.getFechaExpiracion(),
                e.isUsado(), e.getIntentosFallidos());
    }

    public static Categoria aDominio(CategoriaEntity e) {
        return new Categoria(e.getId(), e.getNombre(), e.isActiva());
    }

    public static Producto aDominio(ProductoEntity e) {
        return new Producto(e.getId(), e.getNombre(), e.getDescripcion(), e.getPrecio(), e.getCategoria().getId(),
                e.getCategoria().getNombre(), e.isActivo());
    }

    public static DetallePedido aDominio(DetallePedidoEntity e) {
        return new DetallePedido(e.getId(), e.getProducto().getId(), e.getProducto().getNombre(), e.getCantidad(),
                e.getPrecioUnitario(), e.getNota());
    }

    public static Pedido aDominio(PedidoEntity e) {
        return new Pedido(e.getId(), e.getNumeroMesa(), e.getMesero().getId(), e.getMesero().getNombre(),
                e.getDetalles().stream().map(PersistenceMapper::aDominio).toList(), e.getObservaciones(),
                e.getEstadoCocina(), e.getEstadoPedido(), e.getFechaCreacion(), e.getFechaInicioPreparacion(),
                e.getFechaListo(), e.getFechaEntrega(), e.getFechaCierre());
    }
}
