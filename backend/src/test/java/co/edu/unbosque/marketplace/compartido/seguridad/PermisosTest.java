package co.edu.unbosque.marketplace.compartido.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.expression.EvaluationContext;
import org.springframework.security.access.expression.method.DefaultMethodSecurityExpressionHandler;
import org.springframework.security.authentication.TestingAuthenticationToken;

class PermisosTest {

    private final DefaultMethodSecurityExpressionHandler manejador = new DefaultMethodSecurityExpressionHandler();

    @ParameterizedTest(name = "{0} -> {1}")
    @DisplayName("HUESPED_O_ANFITRION deja pasar al huésped y al anfitrión y niega al administrador")
    @CsvSource({"HUESPED, true", "ANFITRION, true", "ADMINISTRADOR, false"})
    void huespedOAnfitrionSoloAceptaEsosRoles(String rol, boolean permitido) throws NoSuchMethodException {
        var autenticacion = new TestingAuthenticationToken("usuario", null, "ROLE_" + rol);
        MethodInvocation invocacion = mock(MethodInvocation.class);
        when(invocacion.getThis()).thenReturn(this);
        when(invocacion.getMethod()).thenReturn(Object.class.getMethod("toString"));
        EvaluationContext contexto = manejador.createEvaluationContext(autenticacion, invocacion);

        Boolean resultado = manejador.getExpressionParser().parseExpression(Permisos.HUESPED_O_ANFITRION)
                .getValue(contexto, Boolean.class);

        assertThat(resultado).isEqualTo(permitido);
    }
}
