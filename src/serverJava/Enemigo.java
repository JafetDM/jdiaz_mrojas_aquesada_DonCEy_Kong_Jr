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
// Cocodrilo ROJO que sube y baja en UNA liana
// =====================================================
class CocodriloRojoLiana extends Enemigo {

    private final int lianaIndex;
    private final float minY;
    private final float maxY;
    private boolean bajando = true;

    public CocodriloRojoLiana(int lianaIndex,
                              float x,
                              float yInicial,
                              float minY,
                              float maxY,
                              float velocidad) {
        super(x, yInicial, velocidad);
        this.lianaIndex = lianaIndex;
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
        return "CROC_RED_LIANA";
    }
}


// =====================================================
// Cocodrilo ROJO que camina sobre una plataforma
// =====================================================
class CocodriloRojoPlataforma extends Enemigo {

    private final int plataformaIndex;
    private final float minX;
    private final float maxX;
    private boolean moviendoDerecha = true;

    public CocodriloRojoPlataforma(int plataformaIndex,
                                   float xInicial,
                                   float minX,
                                   float maxX,
                                   float y,
                                   float velocidad) {
        super(xInicial, y, velocidad);
        this.plataformaIndex = plataformaIndex;
        this.minX = minX;
        this.maxX = maxX;
    }

    @Override
    public void actualizar(float dt) {
        if (moviendoDerecha) {
            x += velocidad * dt;
            if (x >= maxX) {
                x = maxX;
                moviendoDerecha = false;
            }
        } else {
            x -= velocidad * dt;
            if (x <= minX) {
                x = minX;
                moviendoDerecha = true;
            }
        }
    }

    @Override
    public String getTipo() {
        return "CROC_RED_PLATAFORMA";
    }
}


// =====================================================
// Cocodrilo AZUL que baja por una liana y se cae
// =====================================================
class CocodriloAzul extends Enemigo {

    private final int lianaIndex;
    private final float limiteY;

    public CocodriloAzul(int lianaIndex,
                         float x,
                         float yInicial,
                         float limiteY,
                         float velocidad) {
        super(x, yInicial, velocidad);
        this.lianaIndex = lianaIndex;
        this.limiteY = limiteY;
    }

    @Override
    public void actualizar(float dt) {
        y += velocidad * dt;
        if (y >= limiteY) {
            vivo = false; // el GestorJuego lo elimina
        }
    }

    @Override
    public String getTipo() {
        return "CROC_BLUE_LIANA";
    }
}
