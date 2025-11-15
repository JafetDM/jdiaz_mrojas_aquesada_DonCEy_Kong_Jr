package serverJava;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * GameServer - Gestor de múltiples Publishers (EventPublisher)
 * Maneja las conexiones de clientes y coordina los publishers de cada evento
 */
public class GameServer {
    private final int port;
    
    // Lista de handlers de clientes
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();
    
    // Gestor del juego
    private final GestorJuego gestor;
    
    // ========== PATRÓN OBSERVER: Map de Publishers por Evento ==========
    private final Map<Evento, EventPublisher> publishers = new HashMap<>();
    
    public GameServer(int port) {
        this.port = port;
        this.gestor = new GestorJuego(new GestorJuego.FabricaDKJr());
        
        // Crear un Publisher para cada Evento
        for (Evento evento : Evento.values()) {
            publishers.put(evento, new EventPublisher(evento));
            System.out.println("[*] Publisher creado para " + evento);
        }
    }
    
    // =============== MÉTODOS DEL PATRÓN OBSERVER ===============
    
    /**
     * Suscribe un cliente al publisher de su evento
     * @param subscriber El suscriptor a agregar
     */
    public void subscribe(Subscriber subscriber) {
        if (subscriber == null) {
            System.err.println("[ERROR] Intento de suscribir null");
            return;
        }
        
        Evento evento = subscriber.getEvento();
        EventPublisher publisher = publishers.get(evento);
        
        if (publisher != null) {
            publisher.subscribe(subscriber);
        } else {
            System.err.println("[ERROR] No existe publisher para " + evento);
        }
    }
    
    /**
     * Cancela la suscripción de un cliente
     * @param subscriber El suscriptor a eliminar
     */
    public void unsubscribe(Subscriber subscriber) {
        if (subscriber == null) {
            return;
        }
        
        Evento evento = subscriber.getEvento();
        EventPublisher publisher = publishers.get(evento);
        
        if (publisher != null) {
            publisher.unsubscribe(subscriber);
        }
    }
    
    /**
     * Notifica a todos los suscriptores de un evento específico
     * @param evento El evento
     * @param paquete El paquete a notificar
     */
    public void notifyByEvento(Evento evento, Paquete paquete) {
        EventPublisher publisher = publishers.get(evento);
        if (publisher != null) {
            publisher.notifySubscribers(paquete);
        }
    }
    
    // =============== LÓGICA DEL SERVIDOR ===============
    
    public void start() {
        System.out.println("===========================================");
        System.out.println("Iniciando servidor en el puerto " + port);
        System.out.println("===========================================");
        
        // Iniciar el bucle del juego
        startGameLoop();
        
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            int playerCount = 0;
            
            while (true) {
                // Aceptar cliente
                Socket clientSocket = serverSocket.accept();
                playerCount++;
                String playerName = "Jugador" + playerCount;
                
                // Asignar evento: jugadores impares al JUEGO_1, pares al JUEGO_2
                Evento evento = (playerCount % 2 == 1) ? Evento.JUEGO_1 : Evento.JUEGO_2;
                
                System.out.println("\n[+] " + playerName + " conectado desde " 
                                 + clientSocket.getInetAddress());
                System.out.println("[*] Asignado a: " + evento);
                
                // Crear handler con el evento asignado
                ClientHandler handler = new ClientHandler(clientSocket, playerName, evento, this);
                clients.add(handler);
                subscribe(handler);
                
                // Iniciar thread del handler PRIMERO
                new Thread(handler).start();
                
                // Dar tiempo al thread para inicializarse
                //Thread.sleep(100);
                
                // LUEGO enviar mensaje de bienvenida
                Paquete bienvenida = new Paquete("BIENVENIDA", "Server", 0, 0);
                bienvenida.datos = "Bienvenido " + playerName + " al " + evento;
                handler.update(bienvenida);
                
                System.out.println("✓ " + playerName + " listo en " + evento);
            }
            
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Bucle del juego que actualiza el estado a 60 FPS
     */
    private void startGameLoop() {
        Thread gameThread = new Thread(() -> {
            final float dt = 1.0f / 60.0f;
            long lastBroadcast = System.currentTimeMillis();
            
            while (true) {
                try {
                    // Actualizar gestor del juego
                    gestor.actualizar(dt);
                    
                    // Broadcast del estado cada 100ms (10 veces por segundo)
                    long now = System.currentTimeMillis();
                    if (now - lastBroadcast >= 100) {
                        broadcastGameStates();
                        lastBroadcast = now;
                    }
                    
                    Thread.sleep(16); // ~60 FPS
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    System.err.println("Error en game loop: " + e.getMessage());
                }
            }
        });
        gameThread.setDaemon(true);
        gameThread.start();
        System.out.println("[*] Game loop iniciado a 60 FPS");
    }
    
    /**
     * Hace broadcast del estado de todos los juegos
     */
    private void broadcastGameStates() {
        for (EventPublisher publisher : publishers.values()) {
            publisher.broadcastGameState();
        }
    }
    
    /**
     * Procesa input del jugador y notifica a través del Publisher correspondiente
     * @param sender El handler que envió el input
     * @param paquete El paquete recibido
     */
    public void processPlayerInput(ClientHandler sender, Paquete paquete) {
        if (paquete == null || !paquete.isValid()) {
            System.err.println("Paquete inválido recibido");
            return;
        }
        
        // Solo log de tipos importantes (no MOVIMIENTO)
        if (!paquete.tipo.equals("MOVIMIENTO")) {
            System.out.println("[INPUT] " + paquete);
        }
        
        // Obtener el publisher del evento del cliente
        Evento evento = sender.getEvento();
        EventPublisher publisher = publishers.get(evento);
        
        if (publisher == null) {
            System.err.println("[ERROR] No existe publisher para " + evento);
            return;
        }
        
        // Obtener el GameState del publisher
        GameState gameState = publisher.getGameState();
        
        if (gameState == null) {
            System.err.println("[ERROR] GameState es null para " + evento);
            return;
        }
        
        // Procesar según el tipo de paquete
        switch (paquete.tipo) {
            case "MOVIMIENTO":
                // Actualizar posición del jugador en el GameState
                gameState.actualizarJugador(paquete.playerName, paquete.x, paquete.y);
                
                // Notificar a todos los subscribers del mismo evento
                publisher.notifySubscribers(paquete);
                break;
                
            case "CREAR_ENEMIGO":
                if (paquete.enemyTipo != null) {
                    gestor.crearEnemigo(paquete.enemyTipo, paquete.x, paquete.y);
                    publisher.notifySubscribers(paquete);
                }
                break;
                
            case "CREAR_FRUTA":
                gestor.crearFruta(paquete.x, paquete.y, paquete.puntos);
                publisher.notifySubscribers(paquete);
                break;
                
            default:
                // Retransmitir otros tipos de paquetes
                publisher.notifySubscribers(paquete);
                break;
        }
    }
    
    /**
     * Envía un paquete a todos los clientes (método legacy)
     * @deprecated Usar los publishers directamente
     */
    @Deprecated
    public void sendAll(Paquete paquete) {
        // Enviar a todos los eventos
        for (EventPublisher publisher : publishers.values()) {
            publisher.notifySubscribers(paquete);
        }
    }
    
    /**
     * Elimina un cliente cuando se desconecta
     * @param client El handler del cliente
     */
    public void removeClient(ClientHandler client) {
        clients.remove(client);
        
        // Desuscribir del publisher correspondiente
        unsubscribe(client);
        
        // Eliminar jugador del GameState del publisher
        Evento evento = client.getEvento();
        EventPublisher publisher = publishers.get(evento);
        if (publisher != null) {
            publisher.eliminarJugador(client.getPlayerName());
        }
        
        System.out.println("[-] " + client.getPlayerName() + " desconectado de " + evento);
        
        // Notificar a otros jugadores del mismo evento
        Paquete desconexion = new Paquete("DESCONEXION", client.getPlayerName(), 0, 0);
        notifyByEvento(evento, desconexion);
    }
    
    // ========== MÉTODOS PARA ACCEDER A PUBLISHERS ==========
    
    /**
     * Obtiene el Publisher de un evento específico
     */
    public EventPublisher getPublisher(Evento evento) {
        return publishers.get(evento);
    }
    
    /**
     * Obtiene todos los Publishers
     */
    public Map<Evento, EventPublisher> getAllPublishers() {
        return new HashMap<>(publishers);
    }
    
    /**
     * Obtiene el GameState de un evento específico
     */
    public GameState getGameState(Evento evento) {
        EventPublisher publisher = publishers.get(evento);
        return publisher != null ? publisher.getGameState() : null;
    }
    
    // =============== MÉTODOS DEL GESTOR ===============
    
    public void crearEnemigo(String tipo, float x, float y) {
        gestor.crearEnemigo(tipo, x, y);
    }
    
    public void crearFruta(float x, float y, int puntos) {
        gestor.crearFruta(x, y, puntos);
    }
    
    public List<ElementoJuego> obtenerElementos() {
        return gestor.obtenerElementos();
    }
    
    // =============== MAIN ===============
    
    public static void main(String[] args) {
        int port = 8080;
        
        // Permitir especificar puerto como argumento
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Puerto inválido, usando 8080 por defecto");
            }
        }
        
        GameServer server = new GameServer(port);
        server.start();
    }
}