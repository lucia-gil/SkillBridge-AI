-- =====================================================================
-- Spring Session JDBC (Clase 6.2) - tablas de sesiones en MySQL
-- ---------------------------------------------------------------------
-- La app las crea sola al arrancar (spring.session.jdbc.initialize-schema=always).
-- Este script es para crearlas a mano (p.ej. en RDS con un usuario sin
-- permiso de CREATE). Es idempotente: se puede correr varias veces.
-- Mismo esquema que org/springframework/session/jdbc/schema-mysql.sql.
-- =====================================================================
USE skillbridge_db;

CREATE TABLE IF NOT EXISTS SPRING_SESSION (
    PRIMARY_ID            CHAR(36)     NOT NULL,
    SESSION_ID            CHAR(36)     NOT NULL,
    CREATION_TIME         BIGINT       NOT NULL,
    LAST_ACCESS_TIME      BIGINT       NOT NULL,
    MAX_INACTIVE_INTERVAL INT          NOT NULL,
    EXPIRY_TIME           BIGINT       NOT NULL,
    PRINCIPAL_NAME        VARCHAR(100),
    CONSTRAINT SPRING_SESSION_PK PRIMARY KEY (PRIMARY_ID),
    CONSTRAINT SPRING_SESSION_IX1 UNIQUE (SESSION_ID),
    INDEX SPRING_SESSION_IX2 (EXPIRY_TIME),
    INDEX SPRING_SESSION_IX3 (PRINCIPAL_NAME)
) ENGINE = InnoDB ROW_FORMAT = DYNAMIC;

CREATE TABLE IF NOT EXISTS SPRING_SESSION_ATTRIBUTES (
    SESSION_PRIMARY_ID CHAR(36)     NOT NULL,
    ATTRIBUTE_NAME     VARCHAR(200) NOT NULL,
    ATTRIBUTE_BYTES    BLOB         NOT NULL,
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_PK PRIMARY KEY (SESSION_PRIMARY_ID, ATTRIBUTE_NAME),
    CONSTRAINT SPRING_SESSION_ATTRIBUTES_FK FOREIGN KEY (SESSION_PRIMARY_ID)
        REFERENCES SPRING_SESSION (PRIMARY_ID) ON DELETE CASCADE
) ENGINE = InnoDB ROW_FORMAT = DYNAMIC;

-- Para la sustentacion: sesiones abiertas ahora mismo y de quien son
-- SELECT PRINCIPAL_NAME, FROM_UNIXTIME(LAST_ACCESS_TIME/1000) AS ultimo_acceso,
--        MAX_INACTIVE_INTERVAL/60 AS minutos_inactividad
--   FROM SPRING_SESSION ORDER BY LAST_ACCESS_TIME DESC;
