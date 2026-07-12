package ar.com.viajar.service.pricing;

public enum Zona {
    conurbano(1.0),
    interurbano(1.2),
    caba(0.9);

    private final double factor;

    Zona(double factor) {
        this.factor = factor;
    }

    public double factor() {
        return factor;
    }
}
