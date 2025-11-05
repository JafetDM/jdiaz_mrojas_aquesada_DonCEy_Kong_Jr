// GestorJuego.java
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GestorJuego {

    // ===== fábrica =====
    public interface FabricaObjetos {
        Enemigo crearEnemigo(String tipo, float x, float y);
        Fruta crearFruta(float x, float y, int puntos);
    }

    public static class FabricaDKJr implements FabricaObjetos {

        @Override
        public Enemigo crearEnemigo(String tipo, float x, float y) {
            switch (tipo) {
                case "CROC_RED":
                    return new CocodriloRojo(x, y, 100, 300, 60);
                case "CROC_BLUE":
                    return new CocodriloAzul(x, y, 400, 70);
                default:
                    throw new IllegalArgumentException("Enemigo no soportado: " + tipo);
            }
        }

        @Override
        public Fruta crearFruta(float x, float y, int puntos) {
            return new Fruta(x, y, puntos);
        }
    }

    // ===== manager =====
    private final List<ElementoJuego> elementos = new ArrayList<>();
    private final FabricaObjetos fabrica;

    public GestorJuego(FabricaObjetos fabrica) {
        this.fabrica = fabrica;
    }

    public void crearEnemigo(String tipo, float x, float y) {
        elementos.add(fabrica.crearEnemigo(tipo, x, y));
    }

    public void crearFruta(float x, float y, int puntos) {
        elementos.add(fabrica.crearFruta(x, y, puntos));
    }

    public void actualizar(float dt) {
        Iterator<ElementoJuego> it = elementos.iterator();
        while (it.hasNext()) {
            ElementoJuego e = it.next();
            e.actualizar(dt);
            if (e instanceof Enemigo) {
                Enemigo en = (Enemigo) e;
                if (!en.estaVivo()) {
                    it.remove();
                }
            }
        }
    }

    public List<ElementoJuego> obtenerElementos() {
        return new ArrayList<>(elementos);
    }
}
