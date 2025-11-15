package serverJava;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * EventPublisher - Implementación concreta del patrón Observer (Publisher)
 * Maneja un evento específico y notifica solo a sus suscriptores
 */
public class EventPublisher implements Publisher {
    
    // Evento que maneja este publisher
    private final Evento evento;
    
    // Lista thread-safe de suscriptores
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();
    
    // GameState asociado a este evento
    private final GameState gameState;
    
    /**
     * Constructor
     * @param evento El evento que manejará este publisher
     */
    public EventPublisher(Evento evento) {
        this.evento = evento;
        this.gameState = new GameState(evento);
    }
    
    // =============== IMPLEMENTACIÓN DE PUBLISHER ===============
    
    @Override
    public void subscribe(Subscriber subscriber) {
        if (subscriber == null) {
            System.err.println("[ERROR] Intento de suscribir subscriber null");
            return;
        }
        
        // Verificar que el subscriber pertenece a este evento
        if (subscriber.getEvento() != this.evento) {
            System.err.println("[ERROR] Subscriber de " + subscriber.getEvento() + 
                             " intentó suscribirse a " + this.evento);
            return;
        }
        
        if (!subscribers.contains(subscriber)) {
            subscribers.add(subscriber);
            System.out.println("[+] Suscriptor agregado a " + evento + 
                             ". Total: " + subscribers.size());
        }
    }
    
    @Override
    public void unsubscribe(Subscriber subscriber) {
        if (subscribers.remove(subscriber)) {
            System.out.println("[-] Suscriptor eliminado de " + evento + 
                             ". Total: " + subscribers.size());
        }
    }
    
    @Override
    public void notifySubscribers(Paquete paquete) {
        if (paquete == null) {
            System.err.println("[ERROR] Intento de notificar con paquete null");
            return;
        }
        
        System.out.println("[NOTIFY] " + evento + " -> " + subscribers.size() + 
                         " suscriptores (" + paquete.tipo + ")");
        
        for (Subscriber subscriber : subscribers) {
            try {
                subscriber.update(paquete);
            } catch (Exception e) {
                System.err.println("[ERROR] Error al notificar suscriptor en " + 
                                 evento + ": " + e.getMessage());
            }
        }
    }
    
    // =============== MÉTODOS ESPECÍFICOS DEL EVENTO ===============
    
    /**
     * Procesa un paquete recibido y actualiza el estado
     * @param paquete El paquete a procesar
     */
    public void procesarPaquete(Paquete paquete) {
        if (paquete == null || !paquete.isValid()) {
            return;
        }
        
        // Actualizar gameState según el tipo de paquete
        switch (paquete.tipo) {
            case "MOVIMIENTO":
                gameState.actualizarJugador(paquete.playerName, paquete.x, paquete.y);
                break;
                
            // Agregar más casos según necesites
        }
        
        // Notificar a todos los suscriptores
        notifySubscribers(paquete);
    }
    
    /**
     * Hace broadcast del estado completo del juego
     */
    public void broadcastGameState() {
        Paquete estadoPaquete = new Paquete("ESTADO_JUEGO", "Server", 0, 0);
        estadoPaquete.datos = gameState.toJson();
        notifySubscribers(estadoPaquete);
    }
    
    /**
     * Agrega un jugador al estado
     */
    public void agregarJugador(String playerName, float x, float y) {
        gameState.actualizarJugador(playerName, x, y);
    }
    
    /**
     * Elimina un jugador del estado
     */
    public void eliminarJugador(String playerName) {
        gameState.eliminarJugador(playerName);
    }
    
    // =============== GETTERS ===============
    
    public Evento getEvento() {
        return evento;
    }
    
    public GameState getGameState() {
        return gameState;
    }
    
    public int getCantidadSuscriptores() {
        return subscribers.size();
    }
    
    public List<Subscriber> getSubscribers() {
        return List.copyOf(subscribers); // Copia inmutable
    }
    
    @Override
    public String toString() {
        return "EventPublisher{" +
                "evento=" + evento +
                ", suscriptores=" + subscribers.size() +
                ", jugadores=" + gameState.getCantidadJugadores() +
                '}';
    }
}