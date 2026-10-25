package com.residencial;

import com.residencial.model.Catalogo;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CatalogoTest {

    @Test
    void aceptaVariantesYDevuelveElValorCanonico() {
        assertThat(Catalogo.ESTADO_RESERVA.normalizar("aprobada")).isEqualTo("APROBADA");
        assertThat(Catalogo.ESTADO_OBRA.normalizar(" en proceso ")).isEqualTo("EN_PROCESO");
        assertThat(Catalogo.ESTADO_OBRA.normalizar("En-Proceso")).isEqualTo("EN_PROCESO");
        assertThat(Catalogo.TIPO_HABITANTE.normalizar("Arrendatario")).isEqualTo("ARRENDATARIO");
        assertThat(Catalogo.TIPO_VEHICULO.normalizar("MOTO")).isEqualTo("Moto");
        assertThat(Catalogo.ESTADO_CUENTA.normalizar("al dia")).isEqualTo("Al Día");
        assertThat(Catalogo.ESTADO_CUENTA.normalizar("AL DÍA")).isEqualTo("Al Día");
    }

    @Test
    void rechazaValoresDesconocidosConLaListaPermitida() {
        assertThatThrownBy(() -> Catalogo.ESTADO_RESERVA.normalizar("cancelada"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("PENDIENTE, APROBADA, RECHAZADA");
        assertThatThrownBy(() -> Catalogo.TIPO_VEHICULO.normalizar("avion"))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rechazaVacioONulo() {
        assertThatThrownBy(() -> Catalogo.TIPO_HABITANTE.normalizar(null))
                .hasMessageContaining("es obligatorio");
        assertThatThrownBy(() -> Catalogo.TIPO_HABITANTE.normalizar("  "))
                .hasMessageContaining("es obligatorio");
    }
}
