package serverJava;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Clase Paquete para serializar/deserializar datos del juego
 * Representa un mensaje entre cliente y servidor
 */
public class Paquete {
    // Gson estático para toda la aplicación
    private static final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    
    // Campos del paquete
    public String tipo;           // "MOVIMIENTO", "CREAR_ENEMIGO", "CREAR_FRUTA", "ESTADO_JUEGO", etc.
    public String playerName;     // Nombre del jugador que envía
    public String movimiento;     // "ARRIBA", "ABAJO", "IZQUIERDA", "DERECHA", "QUIETO"
    public float x;               // Posición X
    public float y;               // Posición Y
    public int puntos;            // Puntos (para frutas)
    public String enemyTipo;      // Tipo de enemigo (si aplica)
    public long timestamp;        // Marca de tiempo
    public Object datos;          // Campo genérico para datos adicionales
    
    // Constructor vacío (necesario para Gson)
    public Paquete() {
        this.timestamp = System.currentTimeMillis();
    }
    
    // Constructor con parámetros comunes
    public Paquete(String tipo, String playerName, float x, float y) {
        this.tipo = tipo;
        this.playerName = playerName;
        this.x = x;
        this.y = y;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Constructor para movimiento
    public Paquete(String tipo, String playerName, String movimiento, float x, float y) {
        this(tipo, playerName, x, y);
        this.movimiento = movimiento;
    }
    
    /**
     * Convierte el paquete a JSON
     * @return String con el JSON del paquete
     */
    public String toJson() {
        return gson.toJson(this);
    }
    
    /**
     * Crea un Paquete desde un String JSON
     * @param json String con el JSON
     * @return Paquete deserializado
     */
    public static Paquete fromJson(String json) {
        try {
            return gson.fromJson(json, Paquete.class);
        } catch (Exception e) {
            System.err.println("Error al deserializar JSON: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * Valida que el paquete tenga los datos mínimos necesarios
     * @return true si es válido
     */
    public boolean isValid() {
        return tipo != null && !tipo.isEmpty();
    }
    
    @Override
    public String toString() {
        return "Paquete{" +
                "tipo='" + tipo + '\'' +
                ", playerName='" + playerName + '\'' +
                ", movimiento='" + movimiento + '\'' +
                ", x=" + x +
                ", y=" + y +
                ", timestamp=" + timestamp +
                '}';
    }
    
    // Métodos estáticos de utilidad para crear paquetes comunes
    
    public static Paquete crearMovimiento(String playerName, String movimiento, float x, float y) {
        return new Paquete("MOVIMIENTO", playerName, movimiento, x, y);
    }
    
    public static Paquete crearEnemigo(String playerName, String enemyTipo, float x, float y) {
        Paquete p = new Paquete("CREAR_ENEMIGO", playerName, x, y);
        p.enemyTipo = enemyTipo;
        return p;
    }
    
    public static Paquete crearFruta(String playerName, float x, float y, int puntos) {
        Paquete p = new Paquete("CREAR_FRUTA", playerName, x, y);
        p.puntos = puntos;
        return p;
    }
    
    public static Paquete crearEstadoJuego(Object estadoCompleto) {
        Paquete p = new Paquete();
        p.tipo = "ESTADO_JUEGO";
        p.datos = estadoCompleto;
        return p;
    }
}