package serverJava;
// Enemigo.java

public abstract class Enemigo implements ElementoJuego {

    protected static int SIGUIENTE_ID = 1;

    protected final int id;
    protected float x;
    protected float y;
    protected float velocidad;
    protected boolean vivo = true;

    public Enemigo(float x, float y, float velocidad) {
        this.id = SIGUIENTE_ID++;
        this.x = x;
        this.y = y;
        this.velocidad = velocidad;
    }

    @Override
    public int getId() { return id; }

    @Override
    public float getX() { return x; }

    @Override
    public float getY() { return y; }

    public boolean estaVivo() { return vivo; }

    @Override
    public abstract void actualizar(float dt);

    @Override
    public abstract String getTipo();
}


// =====================================================
// Cocodrilo que sube y baja (ROJO)
// =====================================================
class CocodriloRojo extends Enemigo {

    private final float minY;
    private final float maxY;
    private boolean bajando = true;

    public CocodriloRojo(float x, float yInicial, float minY, float maxY, float velocidad) {
        super(x, yInicial, velocidad);
        this.minY = minY;
        this.maxY = maxY;
    }

    @Override
    public void actualizar(float dt) {
        if (bajando) {
            y += velocidad * dt;
            if (y >= maxY) {
                y = maxY;
                bajando = false;
            }
        } else {
            y -= velocidad * dt;
            if (y <= minY) {
                y = minY;
                bajando = true;
            }
        }
    }

    @Override
    public String getTipo() {
        return "CROC_RED";
    }
}


// =====================================================
// Cocodrilo que baja y se cae (AZUL)
// =====================================================
class CocodriloAzul extends Enemigo {

    private final float limiteY;

    public CocodriloAzul(float x, float yInicial, float limiteY, float velocidad) {
        super(x, yInicial, velocidad);
        this.limiteY = limiteY;
    }

    @Override
    public void actualizar(float dt) {
        y += velocidad * dt;
        if (y >= limiteY) {
            vivo = false; // el manager lo quita
        }
    }

    @Override
    public String getTipo() {
        return "CROC_BLUE";
    }
}
