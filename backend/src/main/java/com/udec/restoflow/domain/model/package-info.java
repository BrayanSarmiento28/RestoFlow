/**
 * Entidades del negocio y sus reglas, en Java puro (sin Spring ni JPA).
 * <ul>
 *   <li>Acceso: Usuario, Rol, CodigoRecuperacion.</li>
 *   <li>Menú: Categoria, Producto.</li>
 *   <li>Pedidos y cocina: Pedido, DetallePedido, EstadoCocina, EstadoPedido.</li>
 *   <li>Inventario: Ingrediente, RecetaIngrediente, MovimientoInventario, TipoMovimiento, UnidadMedida.</li>
 *   <li>Alertas y RestoFlow AI: Alerta, PrioridadAlerta.</li>
 * </ul>
 * Ejemplo de regla: "un pedido solo se puede modificar mientras esté Pendiente".
 */
package com.udec.restoflow.domain.model;
