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
        Enemigo crearEnemigo(String tipo, Float x, Float y);
        Fruta crearFruta(Float x, Float y, Integer puntos);
    }

    public static class FabricaDKJr implements FabricaObjetos {

        // ---- Helpers internos para buscar índice de liana/plataforma más cercana ----
        private Integer findNearestLianaIndex(Float x) {
            List<LayoutDKJr.LianaDef> lianas = LayoutDKJr.getAllLianas();
            Integer bestIdx = 0;
            Float bestDist = Float.MAX_VALUE;

            for (Integer i = 0; i < lianas.size(); i++) {
                Float dist = Math.abs(lianas.get(i).x - x);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestIdx = i;
                }
            }
            return bestIdx;
        }

        private Integer findNearestPlataformaIndex(Float y) {
            List<LayoutDKJr.PlataformaDef> plats = LayoutDKJr.getAllPlataformas();
            Integer bestIdx = 0;
            Float bestDist = Float.MAX_VALUE;

            for (Integer i = 0; i < plats.size(); i++) {
                Float dist = Math.abs(plats.get(i).y - y);
                if (dist < bestDist) {
                    bestDist = dist;
                    bestIdx = i;
                }
            }
            return bestIdx;
        }

        @Override
        public Enemigo crearEnemigo(String tipo, Float x, Float y) {
            // Velocidades “default” que puedes ajustar
            final Float velRojo = 60f;
            final Float velAzul = 80f;

            // ======ROJO EN PLATAFORMA====
            if (tipo.startsWith("CROC_RED_PLATAFORMA_")) {
                // extraer el índice de la plataforma del string
                String sufijo = tipo.substring("CROC_RED_PLATAFORMA_".length());
                Integer idxP = Integer.parseInt(sufijo);

                LayoutDKJr.PlataformaDef p = LayoutDKJr.getPlataforma(idxP);
                Float xInicial = p.xLeft;  // arranca en el borde izquierdo

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
                    Integer idxL = findNearestLianaIndex(x);
                    LayoutDKJr.LianaDef l = LayoutDKJr.getLiana(idxL);

                    Float minY = l.yTop;
                    Float maxY = l.yBottom;
                    Float yInicial = minY; // empieza arriba

                    return new CocodriloRojoLiana(idxL, l.x, yInicial, minY, maxY, velRojo);
                }

                // ====== AZUL EN LIANA (baja y se cae) ======
                case "CROC_BLUE_LIANA":
                case "CROC_BLUE": { // compatibilidad
                    Integer idxL = findNearestLianaIndex(x);
                    LayoutDKJr.LianaDef l = LayoutDKJr.getLiana(idxL);

                    Float yInicial = LayoutDKJr.getYTopLiana(idxL); // un poquito encima de la parte superior
                    Float limiteCaida = l.yBottom + 30.0f;         // se “cae” un poco más abajo de la liana

                    return new CocodriloAzul(idxL, l.x, yInicial, limiteCaida, velAzul);
                }

                default:
                    throw new IllegalArgumentException("Enemigo no soportado: " + tipo);
            }
        }

        @Override
        public Fruta crearFruta(Float x, Float y, Integer puntos) {
            return new Fruta(x, y, puntos);
        }
    }

    // ===== Manager =====
    private final List<ElementoJuego> elementos = new ArrayList<>();
    private final FabricaObjetos fabrica;

    public GestorJuego(FabricaObjetos fabrica) {
        this.fabrica = fabrica;
    }

    public void crearEnemigo(String tipo, Float x, Float y) {
        elementos.add(fabrica.crearEnemigo(tipo, x, y));
    }

    //METODOS PARA FRUTAS
    public void crearFruta(Float x, Float y, Integer puntos) {
        elementos.add(fabrica.crearFruta(x, y, puntos));
    }

    public Boolean eliminarFrutaPorPosicion(Float x, Float y, Float tolerancia) {
        Boolean eliminada = Boolean.FALSE;

        Iterator<ElementoJuego> it = elementos.iterator();
        while (it.hasNext()) {
            ElementoJuego e = it.next();
            if (e instanceof Fruta) {
                Fruta f = (Fruta) e;

                Float dx = f.getX() - x;
                Float dy = f.getY() - y;

                if (Math.abs(dx) <= tolerancia && Math.abs(dy) <= tolerancia) {
                    it.remove();
                    eliminada = Boolean.TRUE;
                    // Si solo querés borrar la primera que coincida:
                    // break;
                }
            }
        }

        return eliminada;
    }

    /**
     * Intenta recolectar una fruta cercana a (x, y).
     * Elimina la fruta de la lista de elementos si la encuentra.
     * @return puntos de la fruta recolectada, o 0 si no había ninguna en rango.
     */
    public Integer recolectarFruta(Float x, Float y, Float tolerancia) {
        Iterator<ElementoJuego> it = elementos.iterator();
        while (it.hasNext()) {
            ElementoJuego e = it.next();
            if (e instanceof Fruta) {
                Fruta f = (Fruta) e;

                Float dx = f.getX() - x;
                Float dy = f.getY() - y;
                Float dist2 = dx * dx + dy * dy;

                if (dist2 <= tolerancia * tolerancia) {
                    Integer puntos = f.getPuntos();
                    it.remove();     // la fruta desaparece del mundo
                    return puntos;   // devolvemos los puntos de ESTA fruta
                }
            }
        }
        // No había ninguna fruta en ese rango
        return 0;
    }

    // Actualiza todos los elementos del juego
    public void actualizar(Float dt) {
        Iterator<ElementoJuego> it = elementos.iterator();
        while (it.hasNext()) {
            ElementoJuego e = it.next();
            e.actualizar(dt);
            if (e instanceof Enemigo) {
                Enemigo en = (Enemigo) e;
                if (!en.estaVivo().booleanValue()) {
                    it.remove();
                }
            }
        }
    }

    public List<ElementoJuego> obtenerElementos() {
        return new ArrayList<>(elementos);
    }
}
