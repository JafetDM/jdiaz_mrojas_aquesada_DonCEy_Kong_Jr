package serverJava;

// Enemigo.java

public abstract class Enemigo implements ElementoJuego {

    protected static Integer SIGUIENTE_ID = 1;

    protected final Integer id;
    protected Float x;
    protected Float y;
    protected Float velocidad;
    protected Boolean vivo = Boolean.TRUE;
    protected String direccion = "DOWN";

    public Enemigo(Float x, Float y, Float velocidad) {
        this.id = SIGUIENTE_ID++;
        this.x = x;
        this.y = y;
        this.velocidad = velocidad;
    }

    @Override
    public Integer getId() { return id; }

    @Override
    public Float getX() { return x; }

    @Override
    public Float getY() { return y; }

    public Boolean estaVivo() { return vivo; }

    public String getDireccion() {
        return direccion;
    }

    @Override
    public abstract void actualizar(Float dt);

    @Override
    public abstract String getTipo();
}


// =====================================================
// Cocodrilo ROJO que sube y baja en UNA liana
// =====================================================
class CocodriloRojoLiana extends Enemigo {

    private final Integer lianaIndex;
    private final Float minY;
    private final Float maxY;
    private Boolean bajando = Boolean.TRUE;

    public CocodriloRojoLiana(Integer lianaIndex,
                              Float x,
                              Float yInicial,
                              Float minY,
                              Float maxY,
                              Float velocidad) {
        super(x, yInicial, velocidad);
        this.lianaIndex = lianaIndex;
        this.minY = minY;
        this.maxY = maxY;
    }

    @Override
    public void actualizar(Float dt) {
        if (bajando.booleanValue()) {
            y += velocidad * dt;
            direccion = "DOWN";

            if (y >= maxY) {
                y = maxY;
                bajando = Boolean.FALSE;
            }
        } else {
            y -= velocidad * dt;
            direccion = "UP";

            if (y <= minY) {
                y = minY;
                bajando = Boolean.TRUE;
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

    private final Integer plataformaIndex;
    private final Float minX;
    private final Float maxX;
    private Boolean moviendoDerecha = Boolean.TRUE;

    public CocodriloRojoPlataforma(Integer plataformaIndex,
                                   Float xInicial,
                                   Float minX,
                                   Float maxX,
                                   Float y,
                                   Float velocidad) {
        super(xInicial, y, velocidad);
        this.plataformaIndex = plataformaIndex;
        this.minX = minX;
        this.maxX = maxX;
    }

    @Override
    public void actualizar(Float dt) {
        if (moviendoDerecha.booleanValue()) {
            x += velocidad * dt;
            direccion = "RIGHT";

            if (x >= maxX) {
                x = maxX;
                moviendoDerecha = Boolean.FALSE;
            }
        } else {
            x -= velocidad * dt;
            direccion = "LEFT";

            if (x <= minX) {
                x = minX;
                moviendoDerecha = Boolean.TRUE;
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

    private final Integer lianaIndex;
    private final Float limiteY;

    public CocodriloAzul(Integer lianaIndex,
                         Float x,
                         Float yInicial,
                         Float limiteY,
                         Float velocidad) {
        super(x, yInicial, velocidad);
        this.lianaIndex = lianaIndex;
        this.limiteY = limiteY;
    }

    @Override
    public void actualizar(Float dt) {
        y += velocidad * dt;
        direccion = "DOWN";
        
        if (y >= limiteY) {
            vivo = Boolean.FALSE; // el GestorJuego lo elimina
        }
    }

    @Override
    public String getTipo() {
        return "CROC_BLUE_LIANA";
    }
}
