package serverJava;
// Fruta.java

public class Fruta implements ElementoJuego {

    private static Integer SIGUIENTE_ID = 1000;

    private final Integer id;
    private final Float x;
    private final Float y;
    private final Integer puntos;
    private final String tipo;
    private static final String[] TIPOS_FRUTA = { "MANGO", "BANANO", "MANZANA" };

    public Fruta(Float x, Float y, Integer puntos) {
        this.id = SIGUIENTE_ID++;
        this.x = x;
        this.y = y;
        this.puntos = puntos;

        // Elegir tipo aleatorio
        Integer idx = (int)(Math.random() * TIPOS_FRUTA.length);
        this.tipo = TIPOS_FRUTA[idx];
    }

    @Override
    public Integer getId() { return id; }

    @Override
    public Float getX() { return x; }

    @Override
    public Float getY() { return y; }

    public Integer getPuntos() { return puntos; }

    public String getTipoFruta() {
        return tipo;
    }

    @Override
    public void actualizar(Float dt) {
        // la fruta no se mueve
    }

    @Override
    public String getTipo() {
        return "FRUTA";
    }
}
