package com.udec.restoflow.infrastructure.adapter.out.persistence;

import com.udec.restoflow.application.port.out.RegistroPeticionRepositoryPort;
import com.udec.restoflow.domain.model.FiltroRegistro;
import com.udec.restoflow.domain.model.RegistroPeticion;
import com.udec.restoflow.domain.model.ResumenRegistro;
import com.udec.restoflow.infrastructure.adapter.out.persistence.entity.RegistroPeticionEntity;
import com.udec.restoflow.infrastructure.adapter.out.persistence.repository.RegistroPeticionJpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Locale;

/** Adaptador de salida: registro de actividad en la tabla "registro_peticiones". */
@Component
public class RegistroPeticionPersistenceAdapter implements RegistroPeticionRepositoryPort {

    private final RegistroPeticionJpaRepository repositorio;

    public RegistroPeticionPersistenceAdapter(RegistroPeticionJpaRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public void guardar(RegistroPeticion r) {
        RegistroPeticionEntity e = new RegistroPeticionEntity();
        e.setFecha(r.fecha());
        e.setRequestId(r.requestId());
        e.setUsuario(r.usuario());
        e.setRol(r.rol());
        e.setMetodo(r.metodo());
        e.setRuta(r.ruta());
        e.setEstado(r.estado());
        e.setDuracionMs(r.duracionMs());
        repositorio.save(e);
    }

    @Override
    public ResumenRegistro.Pagina buscar(FiltroRegistro f, int pagina, int tamano) {
        Page<RegistroPeticionEntity> resultado = repositorio.buscar(f.desde(), f.hasta(), contiene(f.usuario()),
                f.metodo(), contiene(f.ruta()), f.estadoDesde(), f.estadoHasta(), f.duracionMinima(),
                PageRequest.of(pagina, tamano));
        return new ResumenRegistro.Pagina(resultado.getContent().stream().map(this::aDominio).toList(),
                resultado.getTotalElements(), pagina, tamano);
    }

    @Override
    public ResumenRegistro resumir(FiltroRegistro f) {
        Object[] fila = repositorio.resumir(f.desde(), f.hasta(), contiene(f.usuario()), f.metodo(),
                contiene(f.ruta()), f.estadoDesde(), f.estadoHasta(), f.duracionMinima());
        // Algunas bases devuelven la fila dentro de otro arreglo
        Object[] valores = fila.length == 1 && fila[0] instanceof Object[] interna ? interna : fila;
        return new ResumenRegistro(numero(valores[0]), numero(valores[1]), numero(valores[2]), numero(valores[3]),
                Math.round(valores[4] == null ? 0 : ((Number) valores[4]).doubleValue()));
    }

    @Override
    public int borrarAnterioresA(LocalDateTime limite) {
        return repositorio.borrarAnterioresA(limite);
    }

    private RegistroPeticion aDominio(RegistroPeticionEntity e) {
        return new RegistroPeticion(e.getId(), e.getFecha(), e.getRequestId(), e.getUsuario(), e.getRol(),
                e.getMetodo(), e.getRuta(), e.getEstado(), e.getDuracionMs());
    }

    /** "Laura" → "%laura%" para buscar una parte del texto sin importar mayúsculas. */
    private static String contiene(String texto) {
        return texto == null ? null : "%" + texto.toLowerCase(Locale.ROOT) + "%";
    }

    private static long numero(Object valor) {
        return valor == null ? 0 : ((Number) valor).longValue();
    }
}
