package com.residencial.service;

import com.residencial.dto.NotificacionDTO;
import com.residencial.model.Reserva;
import com.residencial.model.Residente;
import com.residencial.model.Vehiculo;
import java.util.List;

/**
 * Las de la sesión actual: las del administrador o, si es un residente, las de su apartamento.
 * Nadie ve, marca ni borra las de otro.
 */
public interface NotificacionService {
    List<NotificacionDTO> listarRecientes();
    long contarNoLeidas();
    void marcarTodasLeidas();
    void eliminarTodas();
    void marcarLeida(Long id);
    void eliminar(Long id);

    // Para el administrador
    void avisarNuevaReserva(Reserva reserva);
    void avisarNuevoVehiculo(Vehiculo vehiculo);

    // Para los residentes del apartamento
    void avisarVehiculoRevisado(Vehiculo vehiculo);
    void avisarReservaRevisada(Reserva reserva);
    // P4 bloque 6: arrendatario que agrega el propietario (al administrador) y su revisión (al apartamento)
    void avisarNuevoResidente(Residente residente);
    void avisarResidenteRevisado(Residente residente, String motivo);
}
