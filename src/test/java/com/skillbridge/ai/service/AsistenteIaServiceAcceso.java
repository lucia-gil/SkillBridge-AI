package com.skillbridge.ai.service;

import com.skillbridge.ai.dto.UsuarioSesion;

/** Acceso de prueba a los métodos de paquete de AsistenteIaService (desde otros paquetes de test). */
public final class AsistenteIaServiceAcceso {
    private AsistenteIaServiceAcceso() {}
    public static String prompt(UsuarioSesion u) { return AsistenteIaService.promptBridgie(u); }
    public static String datos(String nombre, String contenido) { return AsistenteIaService.comoDatos(nombre, contenido); }
}
