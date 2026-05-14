package com.residencial.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApartamentoDetalleResponse {
    private long id;
    private String numero;
    private int piso;
    private String torre;
    private String conjunto;
    private PropietarioResponse propietario;
    private List<ResidenteResponse> residentes;
}
