package com.residencial.exception;

/** Se traduce a 404 {"mensaje": "..."} en ManejadorErrores. */
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
