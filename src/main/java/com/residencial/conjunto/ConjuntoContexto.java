package com.residencial.conjunto;

import java.util.function.Supplier;

/**
 * P7: el conjunto con el que trabaja el hilo actual. Lo fija FiltroConjunto en cada petición (del
 * token o, antes de iniciar sesión, de la cabecera X-Conjunto) y Hibernate lo lee al abrir cada
 * sesión para filtrar y llenar conjunto_id.
 *
 * Sin nada fijado vale el conjunto 1, el que ya existía: así siguen funcionando la app anterior,
 * los datos de demo y las pruebas. RAIZ ve todos los conjuntos; es solo para tareas internas que
 * no pertenecen a ninguno (expirar visitas, buscar a qué conjunto va un evento de Wompi) y nunca
 * debe guardar entidades nuevas.
 */
public final class ConjuntoContexto {

    public static final long PREDETERMINADO = 1L;
    public static final long RAIZ = 0L;

    private static final ThreadLocal<Long> ACTUAL = new ThreadLocal<>();

    private ConjuntoContexto() {}

    public static long actual() {
        Long id = ACTUAL.get();
        return id == null ? PREDETERMINADO : id;
    }

    /** Lo fijado en el hilo, o null; para copiarlo a otro hilo (ver ConjuntoConfig). */
    static Long fijado() {
        return ACTUAL.get();
    }

    static void fijar(Long id) {
        if (id == null) ACTUAL.remove();
        else ACTUAL.set(id);
    }

    /** Corre [tarea] dentro del conjunto [id]; la sesión de Hibernate debe abrirse adentro. */
    public static <T> T en(long id, Supplier<T> tarea) {
        Long antes = ACTUAL.get();
        ACTUAL.set(id);
        try {
            return tarea.get();
        } finally {
            fijar(antes);
        }
    }

    public static void en(long id, Runnable tarea) {
        en(id, () -> {
            tarea.run();
            return null;
        });
    }

    public static <T> T comoRaiz(Supplier<T> tarea) {
        return en(RAIZ, tarea);
    }

    public static void comoRaiz(Runnable tarea) {
        en(RAIZ, tarea);
    }
}
