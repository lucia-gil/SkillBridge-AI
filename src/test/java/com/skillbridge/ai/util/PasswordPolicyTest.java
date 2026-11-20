package com.skillbridge.ai.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/** Las mismas reglas que muestra el medidor de fuerza del registro. */
class PasswordPolicyTest {

    @ParameterizedTest
    @ValueSource(strings = {"Skillbridge#2026", "ClaveSegura12!", "Ñandú-Perú-2026", "AAAAAAAAAA1$"})
    void aceptaContrasenasQueCumplen(String pwd) {
        assertThat(PasswordPolicy.validar(pwd)).isNull();
    }

    @ParameterizedTest
    @NullAndEmptySource
    void rechazaVacia(String pwd) {
        assertThat(PasswordPolicy.validar(pwd)).isEqualTo("La contraseña no puede estar vacía.");
    }

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "Clave Segura 12!   | no debe contener espacios",
            "Corta#1            | al menos 12 caracteres",
            "skillbridge#2026   | al menos una mayúscula",
            "Skillbridge#Peru   | al menos un número",
            "Skillbridge2026    | al menos un símbolo"
    })
    void rechazaConMensajeClaro(String pwd, String fragmento) {
        assertThat(PasswordPolicy.validar(pwd)).contains(fragmento);
    }

    @Test
    void onceCaracteresNoAlcanzan() {
        assertThat(PasswordPolicy.validar("Abcdefgh1#x")).contains("12");     // 11
        assertThat(PasswordPolicy.validar("Abcdefgh1#xy")).isNull();          // 12
    }

    @Test
    void requisitosDescribeLaRegla() {
        assertThat(PasswordPolicy.requisitos()).contains("12").contains("mayúscula").contains("número").contains("símbolo");
    }
}
