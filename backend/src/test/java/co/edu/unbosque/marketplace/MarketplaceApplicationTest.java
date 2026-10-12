package co.edu.unbosque.marketplace;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class MarketplaceApplicationTest {

    @Test
    @DisplayName("El contexto de Spring levanta sin errores")
    void elContextoLevanta() {
        // Si el contexto no carga, la prueba falla por sí sola.
    }
}
