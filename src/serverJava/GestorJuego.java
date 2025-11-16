package serverJava;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GestorJuego {

    // (Estos dos campos tuyos los dejo aunque no se usan aún)
    private List<Subscriber> subscribers = new ArrayList<>();
    private Paquete currentState;

    // ===== Fábrica =====
    public interface FabricaObjetos {
        Enemigo crearEnemigo(String tipo, float x, float y);
        Fruta crearFruta(float x, float y, int puntos);
    }

    public static class FabricaDKJr implements FabricaObjetos {

        // ---- Helpers internos para buscar índice de liana/plataforma más cercana ----
        private int findNearestLianaIndex(float x) {
            List<LayoutDKJr.LianaDef> lianas = LayoutDKJr.getAllLianas();
            int bestIdx = 0;
            float bestDist = Float.MAX_VALUE;

            for (int i = 0; i < lianas.size(); i++) {
                float dist = Math.abs(lianas.get(i).x - x);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestIdx = i;
                }
            }
            return bestIdx;
        }

        private int findNearestPlataformaIndex(float y) {
            List<LayoutDKJr.PlataformaDef> plats = LayoutDKJr.getAllPlataformas();
            int bestIdx = 0;
            float bestDist = Float.MAX_VALUE;

            for (int i = 0; i < plats.size(); i++) {
                float dist = Math.abs(plats.get(i).y - y);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestIdx = i;
                }
            }
            return bestIdx;
        }

        @Override
        public Enemigo crearEnemigo(String tipo, float x, float y) {
            // Velocidades “default” que puedes ajustar
            final float velRojo = 60f;
            final float velAzul = 80f;

            // ======ROJO EN PLATAFORMA====
            if (tipo.startsWith("CROC_RED_PLATAFORMA_")) {
                // extraer el índice de la plataforma del string
                String sufijo = tipo.substring("CROC_RED_PLATAFORMA_".length());
                int idxP = Integer.parseInt(sufijo);

                LayoutDKJr.PlataformaDef p = LayoutDKJr.getPlataforma(idxP);
                float xInicial = p.xLeft;  // arranca en el borde izquierdo

                return new CocodriloRojoPlataforma(
                        idxP,
                        xInicial,
                        p.xLeft,
                        p.xRight,
                        p.y,
                        velRojo
                );
            }

            switch (tipo) {
                // ====== ROJO EN LIANA ======
                case "CROC_RED_LIANA":
                case "CROC_RED": { // compatibilidad por si aún se usa el viejo tipo
                    int idxL = findNearestLianaIndex(x);
                    LayoutDKJr.LianaDef l = LayoutDKJr.getLiana(idxL);

                    float minY = l.yTop;
                    float maxY = l.yBottom;
                    float yInicial = minY; // empieza arriba

                    return new CocodriloRojoLiana(idxL, l.x, yInicial, minY, maxY, velRojo);
                }

                // ====== AZUL EN LIANA (baja y se cae) ======
                case "CROC_BLUE_LIANA":
                case "CROC_BLUE": { // compatibilidad
                    int idxL = findNearestLianaIndex(x);
                    LayoutDKJr.LianaDef l = LayoutDKJr.getLiana(idxL);

                    float yInicial = LayoutDKJr.getYTopLiana(idxL); // un poquito encima de la parte superior
                    float limiteCaida = l.yBottom + 30.0f;         // se “cae” un poco más abajo de la liana

                    return new CocodriloAzul(idxL, l.x, yInicial, limiteCaida, velAzul);
                }

                default:
                    throw new IllegalArgumentException("Enemigo no soportado: " + tipo);
            }
        }

        @Override
        public Fruta crearFruta(float x, float y, int puntos) {
            return new Fruta(x, y, puntos);
        }
    }

    // ===== Manager =====
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
