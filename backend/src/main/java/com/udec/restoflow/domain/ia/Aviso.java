package com.udec.restoflow.domain.ia;

import com.udec.restoflow.domain.model.Rol;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * RestoFlow AI · un aviso para el personal, calculado al momento con los datos reales (no se guarda: cuando la
 * situación se resuelve, el aviso desaparece solo).
 *
 * @param clave     identificador estable (por ejemplo "demora-12"), para saber en el navegador cuáles ya se vieron
 * @param enlace    pantalla donde se atiende (por ejemplo "/cocina")
 * @param desde     desde cuándo pasa (para ordenar y mostrar "hace 5 min")
 * @param para      roles que deben verlo; el ADMIN ve todos
 * @param usuarioId si no es null, un MESERO solo lo ve si es suyo (el pedido de sus mesas)
 */
public record Aviso(String clave, Tipo tipo, Nivel nivel, String titulo, String mensaje, String enlace,
                    LocalDateTime desde, Set<Rol> para, Long usuarioId) {

    /** De más a menos importante (el orden de la lista). */
    public enum Nivel { URGENTE, ATENCION, INFO }

    public enum Tipo { PLATO_DEMORADO, PEDIDO_LISTO, SIN_COBRAR, INSUMO_EN_MINIMO, INSUMO_SE_ACABA, DEMANDA }

    public Aviso {
        para = para == null ? Set.of() : Set.copyOf(para);
    }

    /** El ADMIN ve todo; los demás, lo de su rol; y un mesero, solo lo de sus mesas. */
    public boolean esPara(Rol rol, Long idUsuario) {
        if (rol == Rol.ADMIN) {
            return true;
        }
        if (!para.contains(rol)) {
            return false;
        }
        return rol != Rol.MESERO || usuarioId == null || usuarioId.equals(idUsuario);
    }
}
