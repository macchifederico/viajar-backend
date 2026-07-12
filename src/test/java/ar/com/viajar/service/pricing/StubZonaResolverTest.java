package ar.com.viajar.service.pricing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StubZonaResolverTest {

    private final StubZonaResolver resolver = new StubZonaResolver();

    @Test
    void alwaysResolvesToConurbano() {
        assertEquals(Zona.conurbano, resolver.resolve(-34.6037, -58.3816));
        assertEquals(Zona.conurbano, resolver.resolve(0, 0));
        assertEquals(Zona.conurbano, resolver.resolve(-90, 180));
    }

    @Test
    void conurbanoFactorIsOne() {
        assertEquals(1.0, Zona.conurbano.factor());
    }
}
