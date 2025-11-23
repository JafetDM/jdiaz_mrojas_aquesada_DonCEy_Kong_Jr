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
        float spawnY = LayoutDKJr.getYForPlataforma(0) - 15.0f; // un poquito arriba

        final float PLAYER_RADIUS = 14.0f;
        final float ENEMY_RADIUS  = 16.0f;
        final float FRUIT_RADIUS  = 18.0f;

        // Límite de caída (debajo de la pantalla / piso)
        // Si quieres lo puedes afinar, pero 600 va bien con tu layout
        float fallLimitY = 700.0f;

        long now = System.currentTimeMillis();

        List<FruitState> frutasAEliminar = new ArrayList<>();

        for (PlayerState p : jugadores.values()) {
            float px = p.x;
            float py = p.y;

            // Cooldown de daño
            Long lastHit = ultimoDaño.get(p.playerName);
            long elapsed = (lastHit == null) ? Long.MAX_VALUE : (now - lastHit);
            boolean puedeRecibirDaño = elapsed > HIT_COOLDOWN_MS;

            boolean caida = (py > fallLimitY);
            boolean chocaEnemigo = false;

            // Solo checamos enemigos si ya puede recibir daño
            if (puedeRecibirDaño && !caida) {
                for (EnemyState e : enemigos) {
                    float dx = e.x - px;
                    float dy = e.y - py;
                    float dist2 = dx * dx + dy * dy;

                    float minDist = ENEMY_RADIUS + PLAYER_RADIUS;
                    if (dist2 < minDist * minDist) {
                        chocaEnemigo = true;
                        break;
                    }
                }
            }

            // --- Colisión con frutas (no tienen cooldown, solo suman puntos) ---
            for (FruitState f : frutas) {
                if (f.recolectada) continue;

                float dx = f.x - px;
                float dy = f.y - py;
                float dist2 = dx * dx + dy * dy;

                if (dist2 < FRUIT_RADIUS * FRUIT_RADIUS) {
                    p.puntos += f.puntos;
                    f.recolectada = true;
                    frutasAEliminar.add(f);
                }
            }

            // --- Aplicar daño SOLO si puede recibir daño y hubo caída o enemigo ---
            if (puedeRecibirDaño && (caida || chocaEnemigo)) {

                // Bajar vida
                if (p.vida > 0) {
                    p.vida--;
                }

                // Si llegó a 0 vidas: resetear a 3 y puntos a 0
                if (p.vida <= 0) {
                    p.vida = 3;
                    p.puntos = 0;
                }

                // Respawn en P0 SIEMPRE que muera
                p.x = spawnX;
                p.y = spawnY;

                // Actualizamos cooldown para que no reciba daño inmediato otra vez
                ultimoDaño.put(p.playerName, now);
            }
        }

        // Eliminar frutas del GestorJuego (mundo real del servidor)
        for (FruitState f : frutasAEliminar) {
            gestor.eliminarFrutaPorPosicion(f.x, f.y, 5.0f);
        }

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
