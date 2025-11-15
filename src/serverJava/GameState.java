package serverJava;

import java.util.HashMap;
import java.util.Map;

/**
 * GameState - Estado simple de un juego
 * Contiene solo las posiciones (x, y) de los jugadores por ahora
 */
public class GameState {
    
    // Evento al que pertenece este estado
    private Evento evento;
    
    // Map con los datos de cada jugador: playerName -> Paquete con (x, y)
    private Map<String, Paquete> jugadores;
    
    // Timestamp de última actualización
    private long timestamp;
    
    /**
     * Constructor
     */
    public GameState(Evento evento) {
        this.evento = evento;
        this.jugadores = new HashMap<>();
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Actualiza la posición de un jugador
     */
    public void actualizarJugador(String playerName, float x, float y) {
        Paquete datos = new Paquete("POSICION", playerName, x, y);
        jugadores.put(playerName, datos);
        this.timestamp = System.currentTimeMillis();
    }
    
    /**
     * Obtiene los datos de un jugador
     */
    public Paquete obtenerJugador(String playerName) {
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
    public Map<String, Paquete> obtenerJugadores() {
        return new HashMap<>(jugadores); // Copia defensiva
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
    
    @Override
    public String toString() {
        return "GameState{" +
                "evento=" + evento +
                ", jugadores=" + jugadores.size() +
                ", timestamp=" + timestamp +
                '}';
    }
}
