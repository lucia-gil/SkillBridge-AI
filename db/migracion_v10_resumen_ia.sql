-- ============================================================
-- Migración v10: resumen de hilos del foro con IA
--   (ForoService.generarResumenIa / AsistenteIaService.resumirHilo,
--    botón "Resumir con IA" en foro-hilo.html)
--
-- La app ya aplica estas columnas sola al arrancar
-- (config/InicializadorEsquemaV10.java); este script es para quien
-- prefiera hacerlo a mano. MySQL 8 no tiene "ADD COLUMN IF NOT EXISTS":
-- si alguna columna ya existe, ejecuta solo las que falten.
-- ============================================================

ALTER TABLE foro_publicaciones
    ADD COLUMN resumen_ia             TEXT     DEFAULT NULL COMMENT 'Resumen del hilo generado por el Asistente IA (solo hilo raiz)',
    ADD COLUMN resumen_ia_generado_en DATETIME DEFAULT NULL COMMENT 'Cuando se genero/regenero el resumen';
