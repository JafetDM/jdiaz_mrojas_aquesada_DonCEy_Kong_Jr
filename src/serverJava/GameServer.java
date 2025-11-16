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

        // Iniciar consola de administración
        startAdminConsole();
        
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
    
    // Envía el estado del juego a todos los suscriptores de cada evento
    private void broadcastGameStates() {
        // Obtener snapshot global de elementos
        List<ElementoJuego> elementos = gestor.obtenerElementos();

        for (EventPublisher publisher : publishers.values()) {
            GameState gameState = publisher.getGameState();

            // Actualizar enemigos y frutas dentro del GameState
            gameState.actualizarEnemigosYFrutas(elementos);

            // Enviar ESTADO_JUEGO a los suscriptores de este evento
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

    // =============== MÉTODOS DEL ADMIN ===============

    /**
     * Crear enemigo desde la consola del admin.
     * Recibe el evento, el tipo de enemigo y la ubicación lógica.
     */
    public void crearEnemigoComoAdmin(Evento evento, String tipo, int liana, int plataforma) {
        float x;
        float y;
        String tipoFinal = tipo;

        if ("CROC_BLUE".equalsIgnoreCase(tipo)) {
            LayoutDKJr.LianaDef l = LayoutDKJr.getLiana(liana);
            x = l.x;
            y = LayoutDKJr.getYTopLiana(liana);
            tipoFinal = "CROC_BLUE_LIANA"; // si querés ser más explícito
        } else {
            if (plataforma >= 0) {
                // ROJO EN PLATAFORMA
                LayoutDKJr.PlataformaDef p = LayoutDKJr.getPlataforma(plataforma);
                x = LayoutDKJr.getXCentroPlataforma(plataforma);
                y = p.y;
                tipoFinal = "CROC_RED_PLATAFORMA_" + plataforma;
            } else {
                // ROJO EN LIANA
                LayoutDKJr.LianaDef l = LayoutDKJr.getLiana(liana);
                x = l.x;
                y = LayoutDKJr.getYTopLiana(liana);
                tipoFinal = "CROC_RED_LIANA";
            }
        }

        gestor.crearEnemigo(tipoFinal, x, y);

        Paquete p = Paquete.crearEnemigo("ADMIN", tipoFinal, x, y);
        notifyByEvento(evento, p);

        System.out.println("[ADMIN] Enemigo " + tipoFinal + " creado en " + evento +
                        " (liana=" + liana + ", plataforma=" + plataforma +
                        ") -> (" + x + ", " + y + ")");
    }


    
    /**
     * Crear fruta desde la consola del admin.
     */
    public void crearFrutaComoAdmin(Evento evento, int liana, int alturaIndex, int puntos) {
        float x = LayoutDKJr.getXForLiana(liana);
        float y = LayoutDKJr.getYOnLiana(liana, alturaIndex);

        // 1) Crear en el gestor
        gestor.crearFruta(x, y, puntos);

        // 2) Notificar a los clientes de ese evento
        Paquete p = Paquete.crearFruta("ADMIN", x, y, puntos);
        notifyByEvento(evento, p);

        System.out.println("[ADMIN] Fruta creada en " + evento +
                           " (liana=" + liana + ", altura=" + alturaIndex +
                           ", puntos=" + puntos + ") -> (" + x + ", " + y + ")");
    }

    /**
     * Inicia un hilo que permite a un usuario administrador crear
     * enemigos y frutas desde la consola del servidor.
     */
    private void startAdminConsole() {
        Thread adminThread = new Thread(() -> {
            java.util.Scanner sc = new java.util.Scanner(System.in);

            System.out.println("===========================================");
            System.out.println("[ADMIN] Consola de administración iniciada");
            System.out.println("Comandos disponibles:");
            System.out.println("  enemigo - crear cocodrilo (rojo/azul)");
            System.out.println("  fruta   - crear fruta");
            System.out.println("  map     - mostrar mapa lógico (lianas/plataformas)");
            System.out.println("  ayuda   - mostrar comandos");
            System.out.println("  salir   - terminar consola admin (no apaga el server)");
            System.out.println("===========================================");

            while (true) {
                System.out.print("[ADMIN] > ");
                String cmd = sc.nextLine().trim().toLowerCase();

                if (cmd.equals("salir")) {
                    System.out.println("[ADMIN] Consola de administración finalizada.");
                    break;
                }

                if (cmd.equals("ayuda")) {
                    System.out.println("Comandos:");
                    System.out.println("  enemigo - crear cocodrilo (rojo/azul)");
                    System.out.println("  fruta   - crear fruta");
                    System.out.println("  map     - mostrar mapa lógico (lianas/plataformas)");
                    System.out.println("  salir   - salir de la consola admin");
                    continue;
                }

                if (cmd.equals("map")) {
                    printMap();
                    continue;
                }

                if (cmd.equals("enemigo")) {
                    try {
                        System.out.print("  Tipo (CROC_RED/CROC_BLUE): ");
                        String tipo = sc.nextLine().trim();

                        System.out.print("  Evento (1 = JUEGO_1, 2 = JUEGO_2): ");
                        int idxEv = Integer.parseInt(sc.nextLine().trim()) - 1;
                        Evento evento = Evento.fromIndex(idxEv);

                        int liana = -1;
                        int plataforma = -1;

                        if ("CROC_BLUE".equalsIgnoreCase(tipo)) {
                            // SOLO liana
                            System.out.print("  Liana (0,1,2,...): ");
                            liana = Integer.parseInt(sc.nextLine().trim());
                            plataforma = -1; // no aplica

                        } else { // CROC_RED
                            System.out.print("  ¿Dónde? (1 = liana, 2 = plataforma): ");
                            int destino = Integer.parseInt(sc.nextLine().trim());

                            if (destino == 1) {
                                System.out.print("  Liana (0,1,2,...): ");
                                liana = Integer.parseInt(sc.nextLine().trim());
                                plataforma = -1; // no aplica
                            } else {
                                System.out.print("  Plataforma (0,1,2,...): ");
                                plataforma = Integer.parseInt(sc.nextLine().trim());
                                // la liana no se usa cuando plataforma >= 0,
                                // pero ponemos algún valor válido por si acaso
                                liana = 0;
                            }
                        }

                        crearEnemigoComoAdmin(evento, tipo, liana, plataforma);

                    } catch (Exception e) {
                        System.out.println("[ADMIN] Error leyendo datos de enemigo: " + e.getMessage());
                    }
                    continue;
                }

                if (cmd.equals("fruta")) {
                    try {
                        System.out.print("  Evento (1 = JUEGO_1, 2 = JUEGO_2): ");
                        int idxEv = Integer.parseInt(sc.nextLine().trim()) - 1;
                        Evento evento = Evento.fromIndex(idxEv);

                        System.out.print("  Liana (0,1,2,...): ");
                        int liana = Integer.parseInt(sc.nextLine().trim());

                        System.out.print("  Altura en la liana (0 = arriba, 1 = medio, 2 = abajo): ");
                        int altura = Integer.parseInt(sc.nextLine().trim());

                        System.out.print("  Puntos de la fruta: ");
                        int puntos = Integer.parseInt(sc.nextLine().trim());

                        crearFrutaComoAdmin(evento, liana, altura, puntos);

                    } catch (Exception e) {
                        System.out.println("[ADMIN] Error leyendo datos de fruta: " + e.getMessage());
                    }
                    continue;
                }

                System.out.println("[ADMIN] Comando desconocido. Escribe 'ayuda' para ver opciones.");
            }
        });

        adminThread.setDaemon(true); // no impide que el server se cierre si el main termina
        adminThread.start();
    }

    // Imprime el mapa lógico (lianas y plataformas) en la consola
    private void printMap() {
        System.out.println("===========================================");
        System.out.println("              MAPA LÓGICO DK JR           ");
        System.out.println("===========================================");

        // ---- Lianas ----
        try {
            int numLianas = LayoutDKJr.getCantidadLianas();
            System.out.println("-- LIANAS (índice -> x aproximada) --");
            for (int i = 0; i < numLianas; i++) {
                float x = LayoutDKJr.getXForLiana(i);
                System.out.printf("  Liana %d -> x = %.1f%n", i, x);
            }
        } catch (Exception e) {
            System.out.println("[WARN] No se pudo obtener lista de lianas desde LayoutDKJr: " + e.getMessage());
        }

        // ---- Plataformas ----
        try {
            int numPlataformas = LayoutDKJr.getCantidadPlataformas();
            System.out.println("-- PLATAFORMAS (índice -> y aproximada) --");
            for (int i = 0; i < numPlataformas; i++) {
                float y = LayoutDKJr.getYForPlataforma(i);
                System.out.printf("  Plataforma %d -> y = %.1f%n", i, y);
            }
        } catch (Exception e) {
            System.out.println("[WARN] No se pudo obtener lista de plataformas desde LayoutDKJr: " + e.getMessage());
        }

        System.out.println("===========================================");
        System.out.println("TIP: Usa estos índices al crear enemigos/frutas.");
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