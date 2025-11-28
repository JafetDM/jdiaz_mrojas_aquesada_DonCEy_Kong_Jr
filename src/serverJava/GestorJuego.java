package serverJava;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class GestorJuego {

    // (Estos dos campos tuyos los dejo aunque no se usan aún)
    private List<Subscriber> subscribers = new ArrayList<>();
    private Paquete currentState;

    // Multiplicador de velocidad acumulado (para nuevos enemigos)
    private Float multiplicadorVelocidad = 1.0f;  

    // ===== Fábrica =====
    public interface FabricaObjetos {
        Enemigo crearEnemigo(String tipo, Float x, Float y, Float multiplicadorVelocidad); 
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
        public Enemigo crearEnemigo(String tipo, Float x, Float y, Float multiplicadorVelocidad) {  
            // Velocidades base (se multiplicarán por el multiplicador)
            final Float velRojoBase = 60f;
            final Float velAzulBase = 80f;
            
            //Aplicar multiplicador de dificultad
            final Float velRojo = velRojoBase * multiplicadorVelocidad;
            final Float velAzul = velAzulBase * multiplicadorVelocidad;
            
            System.out.println("[CREAR ENEMIGO] Velocidad ajustada: " +
                             "Rojo=" + String.format("%.1f", velRojo) + 
                             " Azul=" + String.format("%.1f", velAzul) +
                             " (multiplicador: x" + String.format("%.2f", multiplicadorVelocidad) + ")");

            // ====== ROJO EN PLATAFORMA ======
            if (tipo.startsWith("CROC_RED_PLATAFORMA_")) {
                String sufijo = tipo.substring("CROC_RED_PLATAFORMA_".length());
                Integer idxP = Integer.parseInt(sufijo);

                LayoutDKJr.PlataformaDef p = LayoutDKJr.getPlataforma(idxP);
                Float xInicial = p.xLeft;

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
                case "CROC_RED": {
                    Integer idxL = findNearestLianaIndex(x);
                    LayoutDKJr.LianaDef l = LayoutDKJr.getLiana(idxL);

                    Float minY = l.yTop;
                    Float maxY = l.yBottom;
                    Float yInicial = minY;

                    return new CocodriloRojoLiana(idxL, l.x, yInicial, minY, maxY, velRojo);  
                }

                // ====== AZUL EN LIANA ======
                case "CROC_BLUE_LIANA":
                case "CROC_BLUE": {
                    Integer idxL = findNearestLianaIndex(x);
                    LayoutDKJr.LianaDef l = LayoutDKJr.getLiana(idxL);

                    Float yInicial = LayoutDKJr.getYTopLiana(idxL);
                    Float limiteCaida = l.yBottom + 30.0f;

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
        elementos.add(fabrica.crearEnemigo(tipo, x, y, this.multiplicadorVelocidad));  
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

    /**
     * Aumenta la velocidad de todos los enemigos
     * @param multiplicador Factor de multiplicación (ej: 1.2 = 20% más rápido)
     */
    public void aumentarVelocidadEnemigos(Float multiplicador) {
        Integer enemigosAfectados = 0;
        
        //Actualizar multiplicador acumulado
        this.multiplicadorVelocidad *= multiplicador;
        
        for (ElementoJuego e : elementos) {
            if (e instanceof Enemigo) {
                Enemigo en = (Enemigo) e;
                Float velocidadAnterior = en.velocidad;
                en.velocidad *= multiplicador;
                enemigosAfectados++;
                
                System.out.println("[DIFICULTAD] " + en.getTipo() + 
                                 " velocidad: " + String.format("%.1f", velocidadAnterior) +
                                 " -> " + String.format("%.1f", en.velocidad));
            }
        }
        
        if (enemigosAfectados > 0) {
            System.out.println("[DIFICULTAD] " + enemigosAfectados + 
                             " enemigos acelerados (x" + multiplicador + ")");
            System.out.println("[DIFICULTAD] Multiplicador acumulado: x" + 
                             String.format("%.2f", this.multiplicadorVelocidad));
        }
    }
    
    /**
     * Obtiene la velocidad promedio de los enemigos (para debug)
     */
    public Float getVelocidadPromedioEnemigos() {  
        Float sumaVelocidades = 0.0f;  
        Integer cantidadEnemigos = 0;  
        
        for (ElementoJuego e : elementos) {
            if (e instanceof Enemigo) {
                Enemigo en = (Enemigo) e;
                sumaVelocidades += en.velocidad;
                cantidadEnemigos++;
            }
        }
        
        if (cantidadEnemigos == 0) return 0.0f;
        return sumaVelocidades / cantidadEnemigos;
    }

    /**
     * Resetea el multiplicador de velocidad al valor inicial
     */
    public void resetearDificultad() {
        this.multiplicadorVelocidad = 1.0f;
        System.out.println("[DIFICULTAD] Multiplicador reseteado a x1.0");
    }
    
    /**
     * Obtiene el multiplicador de velocidad actual
     */
    public Float getMultiplicadorVelocidad() {
        return this.multiplicadorVelocidad;
    }

    /**
     * Elimina todos los enemigos del juego
     */
    public void limpiarEnemigos() {
        Integer cantidadEliminada = 0;  
        
        Iterator<ElementoJuego> it = elementos.iterator();
        while (it.hasNext()) {
            ElementoJuego e = it.next();
            if (e instanceof Enemigo) {
                it.remove();
                cantidadEliminada++;
            }
        }
        
        System.out.println("[RESET] " + cantidadEliminada + " enemigos eliminados");
    }
    
    /**
     * Elimina todas las frutas del juego
     */
    public void limpiarFrutas() {
        Integer cantidadEliminada = 0;  
        
        Iterator<ElementoJuego> it = elementos.iterator();
        while (it.hasNext()) {
            ElementoJuego e = it.next();
            if (e instanceof Fruta) {
                it.remove();
                cantidadEliminada++;
            }
        }
        
        System.out.println("[RESET] " + cantidadEliminada + " frutas eliminadas");
    }
    
    /**
     * Resetea completamente el juego (elimina enemigos, frutas, resetea velocidad)
     */
    public void resetearJuegoCompleto() {
        limpiarEnemigos();
        limpiarFrutas();
        resetearDificultad();
        
        System.out.println("[RESET] Juego reseteado completamente");
    }

}
