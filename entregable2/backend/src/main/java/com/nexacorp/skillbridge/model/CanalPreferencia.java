package com.nexacorp.skillbridge.model;

/** Valores válidos para `preferencias_notificacion.canal` (incluye solo_app/solo_mail, que `notificaciones.canal` no tiene). */
public enum CanalPreferencia {
    app,
    mail,
    app_mail,
    solo_app,
    solo_mail
}
