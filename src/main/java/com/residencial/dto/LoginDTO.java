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
}
