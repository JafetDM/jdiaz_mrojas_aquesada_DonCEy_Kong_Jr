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
    
    /**
     * Constructor
     */
    public GameState(Evento evento) {
        this.evento = evento;
        this.jugadores = new HashMap<>();
        this.enemigos = new ArrayList<>();
        this.frutas = new ArrayList<>();
        this.timestamp = System.currentTimeMillis();
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
    }

    public static class FruitState {
        public int id;
        public float x;
        public float y;
        public int puntos;
        public boolean recolectada;
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

                enemigos.add(es);

            } else if (e instanceof Fruta) {
                Fruta fr = (Fruta) e;

                FruitState fs = new FruitState();
                fs.id = fr.getId();
                fs.x = fr.getX();
                fs.y = fr.getY();
                fs.puntos = fr.getPuntos();
                fs.recolectada = false; // por ahora siempre false

                frutas.add(fs);
            }
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
