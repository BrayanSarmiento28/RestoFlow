package com.udec.restoflow.domain.exception;

/** Se buscó un registro por su identificador y no existe. */
public class RecursoNoEncontradoException extends DomainException {

    public RecursoNoEncontradoException(String recurso, Object id) {
        super(recurso + " con id " + id + " no existe");
    }
}
