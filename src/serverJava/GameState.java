package serverJava;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * GameState - Estado simple de un juego
 * Contiene solo las posiciones (x, y) de los jugadores por ahora
 */
public class GameState {
    
    // Evento al que pertenece este estado
    private Evento evento;
    
    // Map con los datos de cada jugador: playerName -> Paquete con (x, y)
    private Map<String, PlayerState> jugadores;

    //Enemigos y frutas
    private List<EnemyState> enemigos;
    private List<FruitState> frutas;
    
    // Timestamp de última actualización
    private long timestamp;

    // Map para controlar el cooldown de daño por jugador
    private Map<String, Long> ultimoDaño;

    // Cooldown en ms entre daños (enemigo/caída)
    private static final long HIT_COOLDOWN_MS = 1000; // 1 segundo
    
    /**
     * Constructor
     */
    public GameState(Evento evento) {
        this.evento = evento;
        this.jugadores = new HashMap<>();
        this.enemigos = new ArrayList<>();
        this.frutas = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
        this.ultimoDaño = new HashMap<>();
    }
    
    /**
     * Actualiza la posición de un jugador
     */
    public void actualizarJugador(String playerName, float x, float y) {
        PlayerState p = jugadores.get(playerName);
        if (p == null) {
            p = new PlayerState();
            p.playerName = playerName;
            p.vida = 3;     // vidas iniciales
            p.puntos = 0;   // puntos iniciales
            jugadores.put(playerName, p);
        }

        p.x = x;
        p.y = y;

        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Suma puntos al jugador indicado, si existe.
     */
    public void sumarPuntosAJugador(String playerName, int puntos) {
        if (puntos <= 0) return;

        PlayerState p = jugadores.get(playerName);
        if (p != null) {
            p.puntos += puntos;
            this.timestamp = System.currentTimeMillis();
        }
    }

    /**
     * Resta vidas al jugador indicado, si existe.
     */
    public void restarVidaAJugador(String playerName, int cantidad) {
        if (cantidad <= 0) return;

        PlayerState p = jugadores.get(playerName);
        if (p != null) {
            p.vida = Math.max(0, p.vida - cantidad);
            this.timestamp = System.currentTimeMillis();
        }
    }
    
    /**
     * Obtiene los datos de un jugador
     */
    public PlayerState obtenerJugador(String playerName) {
        return jugadores.get(playerName);
    }
    
    /**
     * Elimina un jugador del estado
     */
    public void eliminarJugador(String playerName) {
        jugadores.remove(playerName);
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Obtiene todos los jugadores
     */
    public Map<String, PlayerState> obtenerJugadores() {
        return new HashMap<>(jugadores); // copia defensiva
    }

    // Clase interna para el estado de un jugador
    public static class PlayerState {
        public String playerName;
        public float x;
        public float y;
        public int vida;
        public int puntos;
        public boolean trepando;      // está trepando?
        public int lianaActual;       // índice de liana (-1 si no está en ninguna)
        public String estadoMovimiento; // "CAMINANDO", "TREPANDO", "CAYENDO", "SALTANDO"
        
        public PlayerState() {
            this.trepando = false;
            this.lianaActual = -1;
            this.estadoMovimiento = "CAMINANDO";
        }
    }

    // Clases internas para enemigos y frutas
    public static class EnemyState {
        public int id;
        public String tipo;
        public float x;
        public float y;
        public float velocidad;
        public String direccion;
    }

    public static class FruitState {
        public int id;
        public float x;
        public float y;
        public int puntos;
        public boolean recolectada;
        public String tipo;
    }
    
    // Actualiza la lista de enemigos y frutas
    public void actualizarEnemigosYFrutas(List<ElementoJuego> elementos) {
        enemigos.clear();
        frutas.clear();

        if (elementos == null) return;

        for (ElementoJuego e : elementos) {
            if (e instanceof Enemigo) {
                Enemigo en = (Enemigo) e;

                EnemyState es = new EnemyState();
                es.id = en.getId();
                es.tipo = en.getTipo();
                es.x = en.getX();
                es.y = en.getY();
                es.velocidad = en.velocidad; // protegido, pero mismo paquete
                es.direccion = en.getDireccion();

                enemigos.add(es);

            } else if (e instanceof Fruta) {
                Fruta fr = (Fruta) e;

                FruitState fs = new FruitState();
                fs.id = fr.getId();
                fs.x = fr.getX();
                fs.y = fr.getY();
                fs.puntos = fr.getPuntos();
                fs.recolectada = false; // por ahora siempre false
                fs.tipo = fr.getTipoFruta();

                frutas.add(fs);
            }
        }

        this.timestamp = System.currentTimeMillis();
    }

    public void procesarColisionesYRespawns(GestorJuego gestor) {
        if (jugadores.isEmpty()) return;

        // Spawn en la plataforma 0
        float spawnX = LayoutDKJr.getXCentroPlataforma(0);
        float spawnY = LayoutDKJr.getYForPlataforma(0) - 15.0f;

        // RADIOS AJUSTADOS SEGÚN ESTADO DEL JUGADOR
        final float PLAYER_RADIUS_NORMAL = 14.0f;
        final float PLAYER_RADIUS_TREPAR = 24.0f;  // MÁS GRANDE cuando trepa
        final float ENEMY_RADIUS  = 16.0f;
        final float FRUIT_RADIUS  = 18.0f;

        // Límite de caída
        float fallLimitY = 700.0f;

        long now = System.currentTimeMillis();

        List<FruitState> frutasAEliminar = new ArrayList<>();

        for (PlayerState p : jugadores.values()) {
            float px = p.x;
            float py = p.y;

            // AJUSTAR RADIO DE COLISIÓN SEGÚN SI ESTÁ TREPANDO
            float playerRadius = p.trepando ? PLAYER_RADIUS_TREPAR : PLAYER_RADIUS_NORMAL;

            // Cooldown de daño
            Long lastHit = ultimoDaño.get(p.playerName);
            long elapsed = (lastHit == null) ? Long.MAX_VALUE : (now - lastHit);
            boolean puedeRecibirDaño = elapsed > HIT_COOLDOWN_MS;

            //  DETECTAR CAÍDA AL VACÍO
            boolean caida = (py > fallLimitY);
            boolean chocaEnemigo = false;

            //  DETECTAR COLISIÓN CON ENEMIGOS
            if (puedeRecibirDaño && !caida) {
                for (EnemyState e : enemigos) {
                    float dx = e.x - px;
                    float dy = e.y - py;
                    float dist2 = dx * dx + dy * dy;

                    // USAR RADIO AJUSTADO
                    float minDist = ENEMY_RADIUS + playerRadius;
                    
                    if (dist2 < minDist * minDist) {
                        chocaEnemigo = true;
                        
                        // LOG MEJORADO
                        String estado = p.trepando ? "TREPANDO" : "NORMAL";
                        System.out.println("[COLISION] " + p.playerName + 
                                        " [" + estado + "] golpeado por " + e.tipo + 
                                        " | dist=" + String.format("%.1f", Math.sqrt(dist2)) +
                                        " | pos=(" + String.format("%.1f,%.1f", px, py) + ")");
                        break;
                    }
                }
            }

            // --- Colisión con frutas ---
            for (FruitState f : frutas) {
                if (f.recolectada) continue;

                float dx = f.x - px;
                float dy = f.y - py;
                float dist2 = dx * dx + dy * dy;

                if (dist2 < FRUIT_RADIUS * FRUIT_RADIUS) {
                    p.puntos += f.puntos;
                    f.recolectada = true;
                    frutasAEliminar.add(f);
                    System.out.println("[FRUTA] " + p.playerName + 
                                    " recolectó " + f.tipo + " (+" + f.puntos + " pts)");
                }
            }

            //  APLICAR DAÑO SI CORRESPONDE
            if (puedeRecibirDaño && (caida || chocaEnemigo)) {

                // Log del evento
                if (caida) {
                    System.out.println("[CAÍDA] " + p.playerName + 
                                    " cayó al vacío (y=" + String.format("%.1f", py) + ")");
                }

                // 1) Restar vida
                int vidasAntes = p.vida;
                if (p.vida > 0) {
                    p.vida--;
                }
                System.out.println("[VIDA] " + p.playerName + 
                                " -> " + vidasAntes + " => " + p.vida + " vidas");

                // 2) Si llegó a 0 vidas: GAME OVER
                if (p.vida <= 0) {
                    p.vida = 3;
                    p.puntos = 0;
                    System.out.println("[GAME OVER] " + p.playerName + 
                                    " -> vidas=3, puntos=0 (RESET COMPLETO)");
                }

                // 3)RESPAWN FORZOSO
                float oldX = p.x;
                float oldY = p.y;
                
                p.x = spawnX;
                p.y = spawnY;
                
                // 4) Resetear estado de movimiento
                p.trepando = false;
                p.lianaActual = -1;
                p.estadoMovimiento = "CAMINANDO";

                System.out.println("[RESPAWN] " + p.playerName + 
                                " | desde (" + String.format("%.1f,%.1f", oldX, oldY) + 
                                ") => P0 (" + String.format("%.1f,%.1f", spawnX, spawnY) + ")");

                // 5) Actualizar cooldown
                ultimoDaño.put(p.playerName, now);
                
                // FORZAR TIMESTAMP PARA QUE EL CLIENTE RECIBA ACTUALIZACIÓN INMEDIATA
                this.timestamp = now;
            }
        }

        // Eliminar frutas recolectadas
        for (FruitState f : frutasAEliminar) {
            gestor.eliminarFrutaPorPosicion(f.x, f.y, 5.0f);
        }

        // ACTUALIZAR TIMESTAMP SIEMPRE
        this.timestamp = System.currentTimeMillis();
    }


    /**
     * Convierte el GameState a JSON usando Gson
     */
    public String toJson() {
        return JsonUtils.toJson(this);
    }
    
    /**
     * Crea un GameState desde JSON
     */
    public static GameState fromJson(String json) {
        return JsonUtils.fromJson(json, GameState.class);
    }

    // =============== LIANAS ==========================

    /**
     * Detecta si el jugador está cerca de una liana y puede trepar
     */
    private int detectarLianaCercana(float x, float y) {
        final float TOLERANCIA_X = 15.0f; // píxeles de tolerancia horizontal
        
        for (int i = 0; i < LayoutDKJr.getCantidadLianas(); i++) {
            LayoutDKJr.LianaDef liana = LayoutDKJr.getLiana(i);
            
            // Verificar si está cerca horizontalmente
            float dx = Math.abs(liana.x - x);
            if (dx <= TOLERANCIA_X) {
                // Verificar si está dentro del rango vertical de la liana
                if (y >= liana.yTop - 20.0f && y <= liana.yBottom + 20.0f) {
                    return i;
                }
            }
        }
        return -1; // No está cerca de ninguna liana
    }

    /**
     * Actualiza el estado de trepar del jugador
     */
    public void actualizarEstadoTrepar(String playerName, boolean intentaTrepar) {
        PlayerState p = jugadores.get(playerName);
        if (p == null) return;
        
        if (intentaTrepar) {
            // Detectar si está cerca de una liana
            int lianaIndex = detectarLianaCercana(p.x, p.y);
            
            if (lianaIndex >= 0) {
                p.trepando = true;
                p.lianaActual = lianaIndex;
                p.estadoMovimiento = "TREPANDO";
                
                // Ajustar X para centrar en la liana
                LayoutDKJr.LianaDef liana = LayoutDKJr.getLiana(lianaIndex);
                p.x = liana.x;
            }
        } else {
            // Soltar la liana
            p.trepando = false;
            p.lianaActual = -1;
            p.estadoMovimiento = "CAMINANDO";
        }
        
        this.timestamp = System.currentTimeMillis();
    }

    /**
     * Procesa movimiento vertical cuando está trepando
     */
    public void moverEnLiana(String playerName, float deltaY) {
        PlayerState p = jugadores.get(playerName);
        if (p == null || !p.trepando || p.lianaActual < 0) return;
        
        LayoutDKJr.LianaDef liana = LayoutDKJr.getLiana(p.lianaActual);
        
        // Mover en Y
        p.y += deltaY;
        
        // Limitar movimiento dentro de la liana
        if (p.y < liana.yTop) {
            p.y = liana.yTop;
        }
        if (p.y > liana.yBottom) {
            p.y = liana.yBottom;
            // Opcional: soltar al llegar al fondo
            // p.trepando = false;
            // p.lianaActual = -1;
        }
        
        this.timestamp = System.currentTimeMillis();
    }
    
    // =============== GETTERS Y SETTERS ===============
    
    public Evento getEvento() {
        return evento;
    }
    
    public void setEvento(Evento evento) {
        this.evento = evento;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public int getCantidadJugadores() {
        return jugadores.size();
    }

    public List<EnemyState> getEnemigos() { 
        return enemigos; 
    }

    public List<FruitState> getFrutas() { 
        return frutas; 
    }

    @Override
    public String toString() {
        return "GameState{" +
                "evento=" + evento +
                ", jugadores=" + jugadores.size() +
                ", timestamp=" + timestamp +
                '}';
    }
}
