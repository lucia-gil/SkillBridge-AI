package com.skillbridge.ai.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Verificación en dos pasos (TOTP, RFC 6238) propia, sin librerías externas. */
class TotpTest {

    /** Clave del apéndice B del RFC 6238 para SHA-1: "12345678901234567890". */
    private static final byte[] CLAVE_RFC = "12345678901234567890".getBytes(StandardCharsets.US_ASCII);
    private static final String SECRETO_RFC = Totp.base32Codificar(CLAVE_RFC);

    @ParameterizedTest(name = "t={0}s -> {1}")
    @CsvSource({
            // Vectores oficiales del RFC 6238 (8 dígitos) recortados a los 6 últimos
            "59,          287082",
            "1111111109,  081804",
            "1111111111,  050471",
            "1234567890,  005924",
            "2000000000,  279037",
            "20000000000, 353130"
    })
    @DisplayName("Genera los códigos de los vectores de prueba del RFC 6238")
    void codigoCoincideConRfc(long epochSegundos, String esperado) {
        assertThat(Totp.codigo(CLAVE_RFC, epochSegundos / Totp.PASO_SEGUNDOS)).isEqualTo(esperado);
    }

    @Test
    void base32IdaYVuelta() {
        assertThat(SECRETO_RFC).isEqualTo("GEZDGNBVGY3TQOJQGEZDGNBVGY3TQOJQ");
        assertThat(Totp.base32Decodificar(SECRETO_RFC)).isEqualTo(CLAVE_RFC);
        // tolera minúsculas, espacios y relleno "=" (como lo copian algunas apps)
        assertThat(Totp.base32Decodificar("gezd gnbv gy3t qojq gezd gnbv gy3t qojq==")).isEqualTo(CLAVE_RFC);
    }

    @Test
    void base32RechazaCaracteresInvalidos() {
        assertThatThrownBy(() -> Totp.base32Decodificar("ABC1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void secretoGeneradoTiene160BitsYEsAleatorio() {
        String a = Totp.generarSecreto();
        String b = Totp.generarSecreto();
        assertThat(a).hasSize(32).matches("[A-Z2-7]+");
        assertThat(Totp.base32Decodificar(a)).hasSize(20);
        assertThat(a).isNotEqualTo(b);
    }

    @Test
    @DisplayName("Acepta el paso actual y ±1 (30 s de desfase), rechaza ±2")
    void verificarConToleranciaDeUnPaso() {
        long t = 1_111_111_111L;
        String actual = Totp.codigo(CLAVE_RFC, t / 30);
        assertThat(Totp.verificar(SECRETO_RFC, actual, t)).isTrue();
        assertThat(Totp.verificar(SECRETO_RFC, actual, t + 30)).isTrue();
        assertThat(Totp.verificar(SECRETO_RFC, actual, t - 30)).isTrue();
        assertThat(Totp.verificar(SECRETO_RFC, actual, t + 60)).isFalse();
        assertThat(Totp.verificar(SECRETO_RFC, actual, t - 60)).isFalse();
    }

    @Test
    void verificarIgnoraEspaciosDelCodigo() {
        long t = 1_234_567_890L;
        assertThat(Totp.verificar(SECRETO_RFC, "005 924", t)).isTrue();
    }

    @Test
    void verificarRechazaEntradasInvalidas() {
        long t = 1_234_567_890L;
        assertThat(Totp.verificar(null, "005924", t)).isFalse();
        assertThat(Totp.verificar(SECRETO_RFC, null, t)).isFalse();
        assertThat(Totp.verificar(SECRETO_RFC, "12345", t)).isFalse();      // 5 dígitos
        assertThat(Totp.verificar(SECRETO_RFC, "00592a", t)).isFalse();     // con letra
        assertThat(Totp.verificar("secreto-malo!", "005924", t)).isFalse(); // Base32 inválido
        assertThat(Totp.verificar(SECRETO_RFC, "000000", t)).isFalse();
    }

    @Test
    void uriParaAppsAutenticadoras() {
        String uri = Totp.uri("SkillBridge AI", "ana.rios@correo.com", "ABC234");
        assertThat(uri).isEqualTo("otpauth://totp/SkillBridge%20AI:ana.rios%40correo.com?secret=ABC234"
                + "&issuer=SkillBridge%20AI&algorithm=SHA1&digits=6&period=30");
    }
}
