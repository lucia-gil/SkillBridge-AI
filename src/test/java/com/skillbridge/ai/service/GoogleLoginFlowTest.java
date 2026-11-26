package com.skillbridge.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.skillbridge.ai.config.SecurityConfig;
import com.skillbridge.ai.controller.AuthController;
import com.skillbridge.ai.dto.ResultadoLogin;
import com.skillbridge.ai.dto.UsuarioSesion;
import com.skillbridge.ai.model.Usuario;
import com.skillbridge.ai.repository.UsuarioRepository;
import com.skillbridge.ai.security.AccesoDenegadoHandler;
import com.skillbridge.ai.security.GoogleLoginHandler;
import com.skillbridge.ai.security.IdentidadGoogle;
import com.skillbridge.ai.security.LoginExitosoHandler;
import com.skillbridge.ai.security.LoginFallidoHandler;
import com.skillbridge.ai.security.SesionSegura;
import com.skillbridge.ai.security.SkillBridgeAuthenticationProvider;
import com.skillbridge.ai.util.Roles;
import com.skillbridge.ai.util.SesionKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.jwt.JwtDecoderFactory;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringJUnitWebConfig(GoogleLoginFlowTest.WebTest.class)
@TestPropertySource(properties = "app.auth.google.enabled=true")
class GoogleLoginFlowTest {

    private static final String LOGIN = "/auth/login.html";
    private static final String RETORNO = "/auth/google/callback";
    private static final String EMISOR = "https://accounts.google.com";
    private static final String CLIENTE = "skillbridge-test";
    private static final String CONTEXTO = HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY;

    @Autowired WebApplicationContext mvcContext;
    @Autowired JdbcTemplate jdbc;
    @Autowired GoogleDePrueba proveedor;
    @MockBean UsuarioRepository usuarios;
    @MockBean AuthService auth;
    @MockBean AuditoriaService auditoria;
    @MockBean ConfiguracionService config;
    @MockBean DobleFactorService dobleFactor;
    @MockBean NotificacionService notificaciones;
    @MockBean ConsultaPeruDniService consultaDni;
    @MockBean IdentidadDniService identidadDni;

    private MockMvc mvc;
    private Usuario cuenta;

    @BeforeEach
    void preparar() {
        mvc = MockMvcBuilders.webAppContextSetup(mvcContext).apply(springSecurity()).build();
        jdbc.update("DELETE FROM usuarios_google");
        proveedor.identidad = new Identidad("google-1", "Persona@Gmail.com", true);
        proveedor.nonceIncorrecto = false;
        proveedor.audienciaIncorrecta = false;

        cuenta = new Usuario();
        cuenta.setId(17L);
        cuenta.setCorreo("persona@gmail.com");
        cuenta.setNombreCompleto("Persona Registrada");
        cuenta.setRolOrganizacional(Roles.RESOURCE_MANAGER);
        when(usuarios.buscarPorCorreoParaVinculoGoogle(anyString())).thenAnswer(i ->
                cuenta.getCorreo().equalsIgnoreCase(i.getArgument(0)) ? Optional.of(cuenta) : Optional.empty());
        when(usuarios.buscarParaVinculoGoogle(17L)).thenReturn(Optional.of(cuenta));
        when(usuarios.findById(17L)).thenReturn(Optional.of(cuenta));
        when(auth.prepararSesion(cuenta)).thenAnswer(i -> sesionLocal());
    }

    @Test
    void gmailRegistradoConContrasenaConservaUsuarioPerfilYRol() throws Exception {
        MvcResult resultado = callback(iniciar());
        assertThat(resultado.getResponse().getRedirectedUrl()).isEqualTo("/resource-manager/inicio.html");
        verificarSesion(resultado);
        verificarVinculo("google-1", 17L);
        verify(usuarios, never()).save(any(Usuario.class));

        proveedor.identidad = new Identidad("google-1", "direccion-nueva@gmail.com", true);
        verificarSesion(callback(iniciar()));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios_google", Integer.class)).isEqualTo(1);
    }

    @Test
    void googleConCorreoExternoPideContrasenaYSegundoFactorAntesDeVincular() throws Exception {
        cuenta.setCorreo("persona@example.org");
        proveedor.identidad = new Identidad("google-1", cuenta.getCorreo(), true);
        when(auth.intentarLogin(eq(cuenta.getCorreo()), eq("clave-local"), anyString()))
                .thenAnswer(i -> ResultadoLogin.exito(sesionLocal()));
        when(dobleFactor.estaActivo(17L)).thenReturn(true);
        when(dobleFactor.verificar(17L, "123456")).thenReturn(true);

        MvcResult pasoGoogle = callback(iniciar());
        MockHttpSession pendiente = sesion(pasoGoogle);
        assertThat(pasoGoogle.getResponse().getRedirectedUrl()).isEqualTo(LOGIN);
        assertThat(pendiente.getAttribute(CONTEXTO)).isNull();
        assertThat(pendiente.getAttribute(IdentidadGoogle.ATRIBUTO_PENDIENTE)).isNotNull();
        mvc.perform(get(LOGIN).session(pendiente)).andExpect(model().attribute("correoGoogle", cuenta.getCorreo()));

        MvcResult pasoClave = mvc.perform(post(LOGIN).session(pendiente).with(csrf())
                        .param("email", cuenta.getCorreo()).param("password", "clave-local").param("vincularGoogle", "1"))
                .andExpect(redirectedUrl("/auth/verificar-2fa.html")).andReturn();
        assertThat(sesion(pasoClave).getAttribute(CONTEXTO)).isNull();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios_google", Integer.class)).isZero();

        MvcResult pasoTotp = mvc.perform(post("/auth/verificar-2fa.html").session(sesion(pasoClave)).with(csrf())
                        .param("codigo", "123456"))
                .andExpect(redirectedUrl("/resource-manager/inicio.html")).andReturn();
        verificarSesion(pasoTotp);
        verificarVinculo("google-1", 17L);

        MvcResult siguiente = callback(iniciar());
        assertThat(siguiente.getResponse().getRedirectedUrl()).isEqualTo("/auth/verificar-2fa.html");
        assertThat(sesion(siguiente).getAttribute(CONTEXTO)).isNull();
    }

    @Test
    void unaCuentaInactivaQuedaFueraInclusoCuandoYaEstaVinculada() throws Exception {
        verificarSesion(callback(iniciar()));
        cuenta.setEstado("inactivo");

        MvcResult intento = callback(iniciar());
        assertThat(intento.getResponse().getRedirectedUrl()).isEqualTo(LOGIN);
        assertThat(sesion(intento).getAttribute(CONTEXTO)).isNull();
        verificarVinculo("google-1", 17L);
    }

    @Test
    void correoSinRegistroONoVerificadoNoAbreSesionNiCreaVinculo() throws Exception {
        proveedor.identidad = new Identidad("google-1", "sin-registro@gmail.com", true);
        verificarRechazo(callback(iniciar()));

        proveedor.identidad = new Identidad("google-1", cuenta.getCorreo(), false);
        verificarRechazo(callback(iniciar()));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios_google", Integer.class)).isZero();
    }

    @Test
    void unSegundoSubjectNoPuedeReemplazarElVinculoDelMismoCorreo() throws Exception {
        verificarSesion(callback(iniciar()));
        proveedor.identidad = new Identidad("google-2", cuenta.getCorreo(), true);

        verificarRechazo(callback(iniciar()));
        verificarVinculo("google-1", 17L);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios_google", Integer.class)).isEqualTo(1);
    }

    @Test
    void seRechazanEstadoNonceOAudienciaIncorrectosYSePuedeCancelar() throws Exception {
        Autorizacion solicitud = iniciar();
        verificarRechazo(mvc.perform(get(RETORNO).session(solicitud.sesion())
                        .param("state", "estado-incorrecto").param("code", "codigo"))
                .andExpect(status().is3xxRedirection()).andReturn());

        proveedor.nonceIncorrecto = true;
        verificarRechazo(callback(iniciar()));
        proveedor.nonceIncorrecto = false;
        proveedor.audienciaIncorrecta = true;
        verificarRechazo(callback(iniciar()));

        Autorizacion cancelar = iniciar();
        MvcResult cancelado = mvc.perform(get(RETORNO).session(cancelar.sesion())
                        .param("state", cancelar.estado()).param("error", "access_denied"))
                .andExpect(redirectedUrl(LOGIN)).andReturn();
        mvc.perform(get(LOGIN).session(sesion(cancelado)))
                .andExpect(model().attribute("error", "Cancelaste el acceso con Google. Puedes volver a intentarlo."));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM usuarios_google", Integer.class)).isZero();
    }

    private Autorizacion iniciar() throws Exception {
        MvcResult inicio = mvc.perform(get("/auth/google/iniciar/google"))
                .andExpect(status().is3xxRedirection()).andReturn();
        var destino = URI.create(inicio.getResponse().getRedirectedUrl());
        assertThat(destino.getHost()).isEqualTo("accounts.google.com");
        var parametros = UriComponentsBuilder.newInstance().query(destino.getQuery()).build().getQueryParams();
        assertThat(parametros.getFirst("nonce")).isNotBlank();
        assertThat(parametros.getFirst("code_challenge_method")).isEqualTo("S256");
        assertThat(parametros.getFirst("prompt")).isEqualTo("select_account");
        return new Autorizacion(sesion(inicio), parametros.getFirst("state"));
    }

    private MvcResult callback(Autorizacion inicio) throws Exception {
        return mvc.perform(get(RETORNO).session(inicio.sesion()).param("state", inicio.estado()).param("code", "codigo"))
                .andExpect(status().is3xxRedirection()).andReturn();
    }

    private static MockHttpSession sesion(MvcResult respuesta) {
        return (MockHttpSession) respuesta.getRequest().getSession(false);
    }

    private void verificarSesion(MvcResult resultado) {
        var sesion = sesion(resultado);
        assertThat(sesion.getAttribute(SesionKeys.USUARIO)).isInstanceOfSatisfying(UsuarioSesion.class, usuario -> {
            assertThat(usuario.getUsuarioId()).isEqualTo(17L);
            assertThat(usuario.getPerfilId()).isEqualTo(31L);
            assertThat(usuario.getRolOrganizacional()).isEqualTo(Roles.RESOURCE_MANAGER);
        });
        assertThat(sesion.getAttribute(CONTEXTO)).isInstanceOfSatisfying(SecurityContext.class, contexto ->
                assertThat(contexto.getAuthentication().getPrincipal()).isInstanceOf(UsuarioSesion.class));
    }

    private static void verificarRechazo(MvcResult resultado) {
        assertThat(resultado.getResponse().getRedirectedUrl()).isEqualTo(LOGIN);
        assertThat(sesion(resultado).getAttribute(CONTEXTO)).isNull();
        assertThat(sesion(resultado).getAttribute(SesionKeys.USUARIO)).isNull();
    }

    private void verificarVinculo(String sub, Long usuarioId) {
        assertThat(jdbc.queryForObject("SELECT usuario_id FROM usuarios_google WHERE google_sub = ?", Long.class, sub))
                .isEqualTo(usuarioId);
    }

    private UsuarioSesion sesionLocal() {
        var usuario = new UsuarioSesion();
        usuario.setUsuarioId(cuenta.getId());
        usuario.setPerfilId(31L);
        usuario.setCorreo(cuenta.getCorreo());
        usuario.setRolEfectivo(cuenta.getRolOrganizacional());
        usuario.setRolOrganizacional(cuenta.getRolOrganizacional());
        return usuario;
    }

    record Autorizacion(MockHttpSession sesion, String estado) {}
    record Identidad(String sub, String correo, boolean verificado) {}

    static class GoogleDePrueba implements OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> {
        final RSAKey llave = new RSAKeyGenerator(2048).generate();
        Identidad identidad;
        boolean nonceIncorrecto;
        boolean audienciaIncorrecta;

        GoogleDePrueba() throws JOSEException {}

        @Override
        public OAuth2AccessTokenResponse getTokenResponse(OAuth2AuthorizationCodeGrantRequest solicitud) {
            var autorizacion = solicitud.getAuthorizationExchange().getAuthorizationRequest();
            var nonce = autorizacion.getAdditionalParameters().get("nonce");
            var ahora = Instant.now();
            var claims = new JWTClaimsSet.Builder().issuer(EMISOR)
                    .audience(audienciaIncorrecta ? "otro-cliente" : CLIENTE)
                    .subject(identidad.sub()).issueTime(Date.from(ahora)).expirationTime(Date.from(ahora.plusSeconds(300)))
                    .claim("email", identidad.correo()).claim("email_verified", identidad.verificado())
                    .claim("nonce", nonceIncorrecto ? "otro-nonce" : nonce).build();
            var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build(), claims);
            try {
                jwt.sign(new RSASSASigner(llave));
            } catch (JOSEException ex) {
                throw new IllegalStateException(ex);
            }
            return OAuth2AccessTokenResponse.withToken("access-token-de-prueba")
                    .tokenType(OAuth2AccessToken.TokenType.BEARER).expiresIn(300)
                    .scopes(Set.of("openid", "email")).additionalParameters(Map.of("id_token", jwt.serialize())).build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableWebMvc
    @Import({SecurityConfig.class, AuthController.class, GoogleAccessService.class, GoogleLoginHandler.class,
            LoginExitosoHandler.class, LoginFallidoHandler.class, SesionSegura.class, AccesoDenegadoHandler.class,
            SkillBridgeAuthenticationProvider.class})
    static class WebTest {

        @Bean ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean JdbcTemplate jdbcTemplate() {
            var dataSource = new DriverManagerDataSource("jdbc:h2:mem:google_login;DB_CLOSE_DELAY=-1", "sa", "");
            var jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("CREATE TABLE usuarios_google (usuario_id bigint primary key, google_sub varchar(255) unique not null)");
            return jdbc;
        }

        @Bean ClientRegistrationRepository clientes() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("google")
                    .clientId(CLIENTE).clientSecret("local-only").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .scope("openid", "email").issuerUri(EMISOR).authorizationUri(EMISOR + "/o/oauth2/v2/auth")
                    .tokenUri("https://oauth2.googleapis.com/token").jwkSetUri("https://www.googleapis.com/oauth2/v3/certs")
                    .redirectUri("http://localhost" + RETORNO).build());
        }

        @Bean GoogleDePrueba proveedor() throws JOSEException {
            return new GoogleDePrueba();
        }

        @Bean JwtDecoderFactory<ClientRegistration> decoder(GoogleDePrueba proveedor) {
            return cliente -> {
                try {
                    var decoder = NimbusJwtDecoder.withPublicKey(proveedor.llave.toRSAPublicKey()).build();
                    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                            JwtValidators.createDefaultWithIssuer(cliente.getProviderDetails().getIssuerUri()),
                            new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(cliente.getClientId()))));
                    return decoder;
                } catch (JOSEException ex) {
                    throw new IllegalStateException(ex);
                }
            };
        }
    }
}
