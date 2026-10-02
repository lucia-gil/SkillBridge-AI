package com.skillbridge.ai.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.FlashMap;
import org.springframework.web.servlet.support.RequestContextUtils;

import java.io.IOException;

/**
 * Si alguien sube archivos que superan spring.servlet.multipart.* (5 MB por
 * archivo / 20 MB por envío), Spring corta la petición ANTES de llegar al
 * controlador y, sin esto, se veía la página de error genérica. Aquí se
 * vuelve a la página anterior con un mensaje claro (mismo "error" flash que
 * usan todas las pantallas para el toast).
 */
@ControllerAdvice
public class SubidaArchivosAdvice {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public void archivoDemasiadoGrande(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String volver = request.getHeader("Referer");
        if (volver == null || volver.isBlank()) volver = request.getContextPath() + "/";

        FlashMap flash = RequestContextUtils.getOutputFlashMap(request);
        flash.put("error", "El archivo es demasiado grande: cada archivo puede pesar hasta 5 MB.");
        RequestContextUtils.saveOutputFlashMap(volver, request, response);
        response.sendRedirect(volver);
    }
}
