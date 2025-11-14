package serverJava;
// Fruta.java

public class Fruta implements ElementoJuego {

    private static int SIGUIENTE_ID = 1000;

    private final int id;
    private final float x;
    private final float y;
    private final int puntos;

    public Fruta(float x, float y, int puntos) {
        this.id = SIGUIENTE_ID++;
        this.x = x;
        this.y = y;
        this.puntos = puntos;
    }

    @Override
    public int getId() { return id; }

    @Override
    public float getX() { return x; }

    @Override
    public float getY() { return y; }

    public int getPuntos() { return puntos; }

    @Override
    public void actualizar(float dt) {
        // la fruta no se mueve
    }

    @Override
    public String getTipo() {
        return "FRUTA";
    }
}
