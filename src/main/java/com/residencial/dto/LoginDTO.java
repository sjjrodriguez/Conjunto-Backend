package com.residencial.dto;

import lombok.*;

// ─── Login ───────────────────────────────────────────────────────────────────

public class LoginDTO {

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class AdminLoginRequest {
        private String usuario;
        private String contrasena;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ApartamentoLoginRequest {
        private String torre;
        private String apto;
        private String contrasena;
    }

    @Data @NoArgsConstructor @AllArgsConstructor
    public static class ResidenteLoginRequest {
        private String documento;
        private String contrasena;
        // P5: el titular entra con torre, apto y rol (PROPIETARIO o ARRENDATARIO) en vez del documento
        private String torre;
        private String apto;
        private String rol;
    }
}
