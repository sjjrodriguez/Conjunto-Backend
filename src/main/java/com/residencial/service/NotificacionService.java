package com.residencial.service;

import com.residencial.dto.NotificacionDTO;
import com.residencial.model.Reserva;
import com.residencial.model.Vehiculo;
import java.util.List;

public interface NotificacionService {
    List<NotificacionDTO> listarRecientes();
    void marcarTodasLeidas();
    void eliminarTodas();
    void avisarNuevaReserva(Reserva reserva);
    void avisarNuevoVehiculo(Vehiculo vehiculo);
}
