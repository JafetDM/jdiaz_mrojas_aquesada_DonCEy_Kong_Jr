package serverJava;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GameServer - Gestor de múltiples Publishers (EventPublisher)
 * Maneja las conexiones de clientes y coordina los publishers de cada evento
 */
public class GameServer {
    private final Integer port;

    private static final String MAP_IMAGE_PATH = "src/serverJava/assets/mapa.png";
    
    // Lista de handlers de clientes
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    // ================== HELPERS DE CLIENTES / EVENTOS ==================
    private Integer contarClientesPorEvento(Evento evento) {
        Integer count = 0;
        for (ClientHandler c : clients) {
            if (c.getEvento() == evento) {
                count++;
            }
        }
        return count;
    }

    private Boolean tieneClientesEnEvento(Evento evento) {
        return contarClientesPorEvento(evento) > 0;
    }

    private Integer getTotalClientes() {
        return clients.size();
    }
    
    // Gestor del juego por evento
    private final Map<Evento, GestorJuego> gestores = new HashMap<>();

    // ========== PATRÓN OBSERVER: Map de Publishers por Evento ==========
    private final Map<Evento, EventPublisher> publishers = new HashMap<>();

    // Cooldown de daño por enemigo para cada jugador (en milisegundos)
    private final Map<String, Long> ultimoGolpeEnemigo = new ConcurrentHashMap<>();
    // Mapa de espectador -> jugador objetivo (playerName)
    private final Map<String, String> spectatorTarget = new ConcurrentHashMap<>();
    
    public GameServer(Integer port) {
        this.port = port;

        // Crear un Publisher y un GestorJuego para cada Evento
        for (Evento evento : Evento.values()) {
            publishers.put(evento, new EventPublisher(evento));
            gestores.put(evento, new GestorJuego(new GestorJuego.FabricaDKJr()));
            System.out.println("[*] Publisher y GestorJuego creados para " + evento);
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
            Integer playerCount = 0;
            
            while (Boolean.TRUE) {
                // Aceptar cliente
                Socket clientSocket = serverSocket.accept();

                // ============================
                    // Límite global: máximo 6 conexiones (2 jugadores + hasta 4 espectadores)
                    // ============================
                    if (getTotalClientes() >= 6) {
                        System.out.println("[!] Conexión rechazada: máximo de 6 conexiones alcanzado.");

                        try (DataOutputStream tempOut = new DataOutputStream(
                                new BufferedOutputStream(clientSocket.getOutputStream()))) {

                            Paquete pError = new Paquete("ERROR", "Server", 0f, 0f);
                            pError.datos = "Servidor lleno: máximo 6 conexiones (2 jugadores + 4 espectadores).";
                            String jsonError = pError.toJson();

                            tempOut.writeUTF(jsonError);
                            tempOut.flush();
                        } catch (IOException ioe) {
                            System.err.println("[ERROR] Al enviar mensaje de servidor lleno: " + ioe.getMessage());
                        }

                        clientSocket.close();
                        continue; // seguir esperando otra conexión
                    }

                    // Si hay espacio, seguimos normal
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

                // Nota: No registramos automáticamente al jugador aquí porque no
                // sabemos aún si la conexión será de un jugador o un espectador.
                // El registro del jugador se realizará cuando reciba el paquete
                // ROLE desde el cliente (JUGADOR/ESPECTADOR).

                // Iniciar thread del handler PRIMERO
                new Thread(handler).start();

                // LUEGO enviar mensaje de bienvenida
                Paquete bienvenida = new Paquete("BIENVENIDA", "Server", 0f, 0f);
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
            final Float dt = 1.0f / 60.0f;
            Long lastBroadcast = System.currentTimeMillis();
            
            while (Boolean.TRUE) {
                try {
                    // Actualizar todos los gestores de todos los eventos
                    for (GestorJuego g : gestores.values()) {
                        g.actualizar(dt);
                    }
                    
                    // Broadcast del estado cada 100ms (10 veces por segundo)
                    Long now = System.currentTimeMillis();
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
        gameThread.setDaemon(Boolean.TRUE);
        gameThread.start();
        System.out.println("[*] Game loop iniciado a 60 FPS");
    }
    
    // Envía el estado del juego a todos los suscriptores de cada evento
    private void broadcastGameStates() {
        // Para cada evento, usamos SU propio GestorJuego
        for (Map.Entry<Evento, EventPublisher> entry : publishers.entrySet()) {
            Evento evento = entry.getKey();
            EventPublisher publisher = entry.getValue();

            GestorJuego gestorEvento = gestores.get(evento);
            if (gestorEvento == null) {
                continue; // por seguridad
            }

            // Elementos SOLO de este juego
            List<ElementoJuego> elementos = gestorEvento.obtenerElementos();

            GameState gameState = publisher.getGameState();
            if (gameState == null) {
                continue;
            }

            // 1) Actualizar enemigos y frutas dentro del GameState
            gameState.actualizarEnemigosYFrutas(elementos);

            // 2) Aplicar lógica de colisiones + respawn + vidas/puntos
            List<String> jugadoresGameOver = gameState.procesarColisionesYRespawns(gestorEvento);

            //ENVIAR PAQUETE GAME_OVER A CADA JUGADOR QUE PERDIÓ
            if (jugadoresGameOver != null && !jugadoresGameOver.isEmpty()) {
                for (String playerName : jugadoresGameOver) {
                    // Buscar el ClientHandler de este jugador
                    for (ClientHandler client : clients) {
                        if (client.getPlayerName().equals(playerName) && 
                            client.getEvento() == evento) {
                            
                            // Enviar paquete GAME_OVER
                            Paquete gameOver = new Paquete("GAME_OVER", "Server", 0f, 0f);
                            gameOver.vida = 3;
                            gameOver.puntos = 0;
                            client.sendPacket(gameOver);
                            
                            System.out.println("[SERVER] GAME_OVER enviado a " + playerName);
                            break;
                        }
                    }
                }
            }

            // 3) Enviar ESTADO_JUEGO a los suscriptores de este evento
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
        //if (!paquete.tipo.equals("MOVIMIENTO")) { 
        //    System.out.println("[INPUT] " + paquete);
        //}

        // Obtener el evento del cliente
        Evento evento = sender.getEvento();
        EventPublisher publisher = publishers.get(evento);
        GestorJuego gestorEvento = gestores.get(evento);

        if (publisher == null) {
            System.err.println("[ERROR] No existe publisher para " + evento);
            return;
        }
        if (gestorEvento == null) {
            System.err.println("[ERROR] No existe GestorJuego para " + evento);
            return;
        }

        // Obtener el GameState del publisher
        GameState gameState = publisher.getGameState();

        if (gameState == null) {
            System.err.println("[ERROR] GameState es null para " + evento);
            return;
        }

        // Switch cases

        // Procesar según el tipo de paquete
        switch (paquete.tipo) {
            case "ROLE":
                // Cliente indica su rol: "JUGADOR" o "ESPECTADOR"
                if (paquete.movimiento != null && paquete.movimiento.equalsIgnoreCase("ESPECTADOR")) {
                    System.out.println("[ROLE] " + paquete.playerName + " solicitó ESPECTADOR");
                    // Registrar como espectador sólo si el cupo por jugador no está lleno
                    if (publisher != null) {
                        GameState gs = publisher.getGameState();
                        if (gs != null) {
                            // Mapear índice fijo 1->Jugador1, 2->Jugador2
                            Integer requestedIndex = (Integer)(int) Math.round(paquete.x);
                            String targetPlayer = null;
                            if (requestedIndex == 1 || requestedIndex == 2) {
                                targetPlayer = "Jugador" + requestedIndex;
                            }

                            // Verificar si el target existe en el GameState
                            Boolean targetExists = (targetPlayer != null && gs.obtenerJugador(targetPlayer) != null);
                            if (!targetExists.booleanValue()) {
                                // Si no existe aún, registrar espectador sin objetivo
                                spectatorTarget.put(paquete.playerName, null);
                                System.out.println("[ROLE] " + paquete.playerName + " registrado como espectador (objetivo no disponible: " + targetPlayer + ")");
                                Paquete ok = new Paquete("BIENVENIDA", "Server", 0f, 0f);
                                ok.datos = "Conectado como ESPECTADOR (objetivo no disponible yet)";
                                sender.sendPacket(ok);
                            } else {
                                // Contar cuantos espectadores ya miran a ese jugador
                                Integer count = 0;
                                for (String v : spectatorTarget.values()) {
                                    if (targetPlayer.equals(v)) count++;
                                }

                                if (count >= 2) {
                                    Paquete err = new Paquete("ERROR", "Server", 0f, 0f);
                                    err.datos = "Máximo de espectadores para ese jugador alcanzado";
                                    sender.sendPacket(err);
                                    System.out.println("[ROLE] Rechazado espectador " + paquete.playerName + " para objetivo " + targetPlayer);
                                } else {
                                    spectatorTarget.put(paquete.playerName, targetPlayer);
                                    Paquete ok = new Paquete("BIENVENIDA", "Server", 0f, 0f);
                                    ok.datos = "Conectado como ESPECTADOR mirando a " + targetPlayer;
                                    sender.sendPacket(ok);
                                    System.out.println("[ROLE] " + paquete.playerName + " es espectador de " + targetPlayer);
                                }
                            }
                        }
                    }
                } else {
                    // Solicita ser jugador
                    System.out.println("[ROLE] " + paquete.playerName + " solicitó JUGADOR");
                    if (publisher != null) {
                        GameState gs = publisher.getGameState();
                        if (gs != null) {
                            Integer playersCount = gs.obtenerJugadores().size();
                            if (playersCount >= 2) {
                                // Rechazar como jugador: convertir a espectador por defecto
                                Paquete err = new Paquete("ERROR", "Server", 0f, 0f);
                                err.datos = "Máximo de jugadores alcanzado. Se le asignó rol de espectador.";
                                sender.sendPacket(err);

                                // Registrar como espectador con target al primer jugador
                                java.util.List<String> playerNames = new ArrayList<>(gs.obtenerJugadores().keySet());
                                String target = playerNames.isEmpty() ? null : playerNames.get(0);
                                spectatorTarget.put(paquete.playerName, target);
                                System.out.println("[ROLE] " + paquete.playerName + " forzado a espectador");
                            } else {
                                // Aceptar como jugador: registrar en GameState y enviar spawn
                                LayoutDKJr.PlataformaDef p0 = LayoutDKJr.getPlataforma(0);
                                Float spawnX = LayoutDKJr.getXCentroPlataforma(0);
                                Float spawnY = p0.y - 20.0f;
                                gs.actualizarJugador(paquete.playerName, spawnX, spawnY);

                                Paquete ok = new Paquete("BIENVENIDA", "Server", 0f, 0f);
                                ok.datos = "Conectado como JUGADOR";
                                sender.sendPacket(ok);

                                Paquete spawn = new Paquete("MOVIMIENTO", paquete.playerName, spawnX, spawnY);
                                sender.sendPacket(spawn);
                                System.out.println("[ROLE] " + paquete.playerName + " confirmado como jugador y registrado en GameState");
                            }
                        }
                    }
                }
                break;
            case "TREPAR":
                // El cliente indica que quiere trepar
                // Pasamos la X/Y enviada por el cliente para sincronizar posición en la liana
                gameState.actualizarEstadoTrepar(paquete.playerName, Boolean.TRUE, paquete.x, paquete.y);
                // Enviar inmediatamente un paquete MOVIMIENTO con la posición autoritativa
                GameState.PlayerState jugadorTrepar = gameState.obtenerJugador(paquete.playerName);
                if (jugadorTrepar != null) {
                    Paquete movT = Paquete.crearMovimiento(paquete.playerName, "QUIETO", jugadorTrepar.x, jugadorTrepar.y);
                    publisher.notifySubscribers(movT);
                } else {
                    publisher.notifySubscribers(paquete);
                }
                break;

            case "SOLTAR_LIANA":
                // El cliente suelta la liana -> pasar la X/Y del cliente para sincronizar
                // Usamos la variante que recibe (playerName, intentaTrepar, x, y)
                gameState.actualizarEstadoTrepar(paquete.playerName, Boolean.FALSE, paquete.x, paquete.y);
                // Además, notificar inmediatamente un paquete MOVIMIENTO con la nueva posición
                GameState.PlayerState jugadorSoltado = gameState.obtenerJugador(paquete.playerName);
                if (jugadorSoltado != null) {
                    Paquete mov = Paquete.crearMovimiento(paquete.playerName, "QUIETO", jugadorSoltado.x, jugadorSoltado.y);
                    publisher.notifySubscribers(mov);
                } else {
                    publisher.notifySubscribers(paquete);
                }
                break;

            case "MOVER_EN_LIANA":
                // Movimiento vertical mientras trepa
                // El cliente envía deltaY en paquete.y
                gameState.moverEnLiana(paquete.playerName, paquete.y);
                publisher.notifySubscribers(paquete);
                break;

            case "MOVIMIENTO":

                // 1) Actualizar posición del jugador en el GameState

                // Modificar el caso existente para considerar si está trepando
                GameState.PlayerState jugador = gameState.obtenerJugador(paquete.playerName);
                    
                if (jugador != null && jugador.trepando.booleanValue()) {
                    // Si está trepando, solo permitir movimiento vertical
                    gameState.moverEnLiana(paquete.playerName, paquete.y - jugador.y);
                } else {
                    // Movimiento normal
                    gameState.actualizarJugador(paquete.playerName, paquete.x, paquete.y);
                        
                    // Verificar colisiones...
                }

                // 2) Revisar colisión jugador-fruta en el GestorJuego de ESTE evento
                {
                    final Float TOLERANCIA_FRUTA = 18.0f; // píxeles, ajústalo si hace falta

                    Integer puntosGanados = gestorEvento.recolectarFruta(
                            paquete.x,
                            paquete.y,
                            TOLERANCIA_FRUTA
                    );

                    if (puntosGanados > 0) {
                        // Actualizar los puntos del jugador en el GameState
                        gameState.sumarPuntosAJugador(paquete.playerName, puntosGanados);

                        // (Opcional) Notificar a los clientes que se recogió una fruta
                        Paquete pCol = new Paquete("FRUTA_RECOLECTADA",
                                                   paquete.playerName,
                                                   paquete.x,
                                                   paquete.y);
                        pCol.puntos = puntosGanados;
                        publisher.notifySubscribers(pCol);

                        //System.out.println("[COLISION] " + paquete.playerName +" recolectó fruta en " + evento +" (+" + puntosGanados + " pts)");
                    }
                }

                // 4) Notificar movimiento a los demás jugadores (posición actual)
                publisher.notifySubscribers(paquete);
                break;

            case "COLISION_MARIO":
                //Cliente reporta colisión con Mario (MUERTE INSTANTÁNEA)
                GameState.PlayerState jugadorMario = gameState.obtenerJugador(paquete.playerName);
                
                if (jugadorMario != null && jugadorMario.vida > 0) {
                    System.out.println("[COLISION_MARIO] " + paquete.playerName + 
                                     " golpeado por Mario -> GAME OVER INSTANTÁNEO");
                    
                    //MUERTE INSTANTÁNEA: Resetear vidas y puntos
                    jugadorMario.vida = 3;
                    jugadorMario.puntos = 0;
                    
                    System.out.println("[GAME OVER] " + paquete.playerName + 
                                     " -> RESET COMPLETO (Mario letal)");
                    
                    // Forzar broadcast inmediato
                    publisher.broadcastGameState();
                    
                    // Enviar confirmación directa al cliente
                    Paquete gameOver = new Paquete("GAME_OVER_MARIO", "Server", 0f, 0f);
                    gameOver.vida = 3;
                    gameOver.puntos = 0;
                    sender.sendPacket(gameOver);
                }
                break;

            case "CREAR_ENEMIGO":
                if (paquete.enemyTipo != null) {
                    // Crear enemigo SOLO en el gestor de este evento
                    gestorEvento.crearEnemigo(paquete.enemyTipo, paquete.x, paquete.y);
                    publisher.notifySubscribers(paquete);
                }
                break;

            case "CREAR_FRUTA":
                // Crear fruta SOLO en el gestor de este evento
                gestorEvento.crearFruta(paquete.x, paquete.y, paquete.puntos);
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
        
        // También eliminar si era espectador registrado
        if (spectatorTarget.containsKey(client.getPlayerName())) {
            spectatorTarget.remove(client.getPlayerName());
        }
        
        System.out.println("[-] " + client.getPlayerName() + " desconectado de " + evento);
        
        // Notificar a otros jugadores del mismo evento
        Paquete desconexion = new Paquete("DESCONEXION", client.getPlayerName(), 0f, 0f);
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

    // =============== MÉTODOS DEL ADMIN ===============

    // Lee un entero con mensaje y validación básica
    private Integer leerEntero(Scanner sc, String prompt) {
        while (Boolean.TRUE) {
            System.out.print(prompt + ": ");
            String linea = sc.nextLine().trim();
            try {
                return Integer.parseInt(linea);
            } catch (NumberFormatException e) {
                System.out.println("  [ADMIN] Valor inválido, ingrese un número.");
            }
        }
        return null; // nunca llega aquí
    }

    // Lee un entero entre [min, max] inclusive
    private Integer leerEnteroEnRango(Scanner sc, String prompt, Integer min, Integer max) {
        while (Boolean.TRUE) {
            Integer valor = leerEntero(sc, prompt + " (" + min + " - " + max + ")");
            if (valor < min || valor > max) {
                System.out.println("  [ADMIN] Fuera de rango, intente de nuevo.");
            } else {
                return valor;
            }
        }
        return null; // nunca llega aquí
    }

    // Helper para leer el evento (Juego 1 / Juego 2) con validación
    private Evento leerEvento(java.util.Scanner sc) {
        System.out.print("  Evento (1 = JUEGO_1, 2 = JUEGO_2): ");
        String lineaEv = sc.nextLine().trim();
        Integer idxEv;
        try {
            idxEv = Integer.parseInt(lineaEv) - 1;
        } catch (NumberFormatException e) {
            System.out.println("  [ADMIN] Error: evento debe ser 1 o 2.");
            return null;
        }

        if (idxEv < 0 || idxEv >= Evento.values().length) {
            System.out.println("  [ADMIN] Error: evento inválido.");
            return null;
        }

        return Evento.fromIndex(idxEv);
    }

    // Opción 1 del menú: crear enemigo
    private void manejarCrearEnemigo(java.util.Scanner sc) {
        System.out.println("---- Crear ENEMIGO ----");

        // 1) Leer evento
        Evento evento = leerEvento(sc);
        if (evento == null) return;
        // Validar que el evento tenga al menos un cliente activo
        if (!tieneClientesEnEvento(evento).booleanValue()) {
            System.out.println("  [ADMIN] No hay clientes activos en " + evento +
                            ". No se crearán enemigos en este juego.");
            return;
        }

        // 2) Tipo de enemigo (numérico)
        System.out.println("  Tipo de enemigo:");
        System.out.println("    1) Cocodrilo ROJO");
        System.out.println("    2) Cocodrilo AZUL");
        System.out.print("  Opción: ");

        String lineaTipo = sc.nextLine().trim();
        Integer opTipo;
        try {
            opTipo = Integer.parseInt(lineaTipo);
        } catch (NumberFormatException e) {
            System.out.println("  [ADMIN] Error: debe ser 1 o 2.");
            return;
        }

        String tipo;
        if (opTipo == 1) {
            tipo = "CROC_RED";
        } else if (opTipo == 2) {
            tipo = "CROC_BLUE";
        } else {
            System.out.println("  [ADMIN] Error: tipo inválido (usa 1 o 2).");
            return;
        }

        // 3) Si es AZUL -> siempre en liana
        if ("CROC_BLUE".equals(tipo)) {
            System.out.println("  (AZUL siempre baja por una liana)");
            System.out.print("  Liana (0.." + (LayoutDKJr.getCantidadLianas() - 1) + "): ");
            String lineaL = sc.nextLine().trim();

            Integer liana;
            try {
                liana = Integer.parseInt(lineaL);
            } catch (NumberFormatException e) {
                System.out.println("  [ADMIN] Error: la liana debe ser numérica.");
                return;
            }

            if (liana < 0 || liana >= LayoutDKJr.getCantidadLianas()) {
                System.out.println("  [ADMIN] Error: liana fuera de rango.");
                return;
            }

            Integer plataforma = -1; // no aplica
            crearEnemigoComoAdmin(evento, tipo, liana, plataforma);
            return;
        }

        // 4) Si es ROJO -> elegir destino (liana o plataforma)
        System.out.println("  ¿Dónde aparece el ROJO?");
        System.out.println("    1) Liana");
        System.out.println("    2) Plataforma");
        System.out.print("  Opción: ");

        String lineaDest = sc.nextLine().trim();
        Integer opDest;
        try {
            opDest = Integer.parseInt(lineaDest);
        } catch (NumberFormatException e) {
            System.out.println("  [ADMIN] Error: debe ser 1 o 2.");
            return;
        }

        if (opDest == 1) {
            // ROJO EN LIANA
            System.out.print("  Liana (0.." + (LayoutDKJr.getCantidadLianas() - 1) + "): ");
            String lineaL = sc.nextLine().trim();

            Integer liana;
            try {
                liana = Integer.parseInt(lineaL);
            } catch (NumberFormatException e) {
                System.out.println("  [ADMIN] Error: la liana debe ser numérica.");
                return;
            }

            if (liana < 0 || liana >= LayoutDKJr.getCantidadLianas()) {
                System.out.println("  [ADMIN] Error: liana fuera de rango.");
                return;
            }

            Integer plataforma = -1; // no aplica
            crearEnemigoComoAdmin(evento, tipo, liana, plataforma);

        } else if (opDest == 2) {
            // ROJO EN PLATAFORMA
            System.out.print("  Plataforma (0.." + (LayoutDKJr.getCantidadPlataformas() - 1) + "): ");
            String lineaP = sc.nextLine().trim();

            Integer plataforma;
            try {
                plataforma = Integer.parseInt(lineaP);
            } catch (NumberFormatException e) {
                System.out.println("  [ADMIN] Error: la plataforma debe ser numérica.");
                return;
            }

            if (plataforma < 0 || plataforma >= LayoutDKJr.getCantidadPlataformas()) {
                System.out.println("  [ADMIN] Error: plataforma fuera de rango.");
                return;
            }

            Integer liana = 0; // no se usa realmente para plataforma, pero ponemos algo válido
            crearEnemigoComoAdmin(evento, tipo, liana, plataforma);

        } else {
            System.out.println("  [ADMIN] Error: destino inválido (usa 1 o 2).");
        }
    }

    // Opción 2 del menú: crear fruta
    private void manejarCrearFruta(java.util.Scanner sc) {
        System.out.println("---- Crear FRUTA ----");

        // 1) Evento
        Evento evento = leerEvento(sc);
        if (evento == null) return;
        // Validar que el evento tenga al menos un cliente activo
        if (!tieneClientesEnEvento(evento).booleanValue()) {
            System.out.println("  [ADMIN] No hay clientes activos en " + evento +
                            ". No se crearán frutas en este juego.");
            return;
        }

        // 2) Liana
        System.out.print("  Liana (0.." + (LayoutDKJr.getCantidadLianas() - 1) + "): ");
        String lineaL = sc.nextLine().trim();
        Integer liana;
        try {
            liana = Integer.parseInt(lineaL);
        } catch (NumberFormatException e) {
            System.out.println("  [ADMIN] Error: la liana debe ser numérica.");
            return;
        }
        if (liana < 0 || liana >= LayoutDKJr.getCantidadLianas()) {
            System.out.println("  [ADMIN] Error: liana fuera de rango.");
            return;
        }

        // 3) Altura
        System.out.println("  Altura en la liana:");
        System.out.println("    0) Arriba");
        System.out.println("    1) Medio");
        System.out.println("    2) Abajo");
        System.out.print("  Opción: ");
        String lineaAlt = sc.nextLine().trim();
        Integer altura;
        try {
            altura = Integer.parseInt(lineaAlt);
        } catch (NumberFormatException e) {
            System.out.println("  [ADMIN] Error: altura debe ser 0, 1 o 2.");
            return;
        }
        if (altura < 0 || altura > 2) {
            System.out.println("  [ADMIN] Error: altura inválida (0, 1, 2).");
            return;
        }

        // 4) Puntos
        System.out.print("  Puntos de la fruta: ");
        String lineaPts = sc.nextLine().trim();
        Integer puntos;
        try {
            puntos = Integer.parseInt(lineaPts);
        } catch (NumberFormatException e) {
            System.out.println("  [ADMIN] Error: los puntos deben ser numéricos.");
            return;
        }

        crearFrutaComoAdmin(evento, liana, altura, puntos);
    }

    // Opción 3 del menú: eliminar fruta
    private void manejarEliminarFruta(Scanner sc) {
        System.out.println("---- Eliminar FRUTA ----");

        // 1) Evento
        Evento evento = leerEvento(sc);
        if (evento == null) return;
        // Validar que el evento tenga al menos un cliente activo
        if (!tieneClientesEnEvento(evento).booleanValue()) {
            System.out.println("  [ADMIN] No hay clientes activos en " + evento +
                            ". No se eliminarán frutas en este juego (estado inactivo).");
            return;
        }

        // 2) Liana
        Integer maxLianas = LayoutDKJr.getCantidadLianas() - 1;
        Integer liana = leerEnteroEnRango(sc, 
                "  Liana", 
                0, 
                maxLianas);

        // 3) Altura
        System.out.println("  Altura en la liana:");
        System.out.println("    0) Arriba");
        System.out.println("    1) Medio");
        System.out.println("    2) Abajo");
        Integer altura = leerEnteroEnRango(sc, 
                "  Opción de altura", 
                0, 
                2);

        // 4) Ejecutar eliminación
        eliminarFrutaComoAdmin(evento, liana, altura);
    }

    // Opción 4 del menú: ver mapa lógico (como imagen)
    private void manejarMostrarMapa() {
        System.out.println("[ADMIN] Abriendo mapa lógico...");

        try {
            // Verificar si Desktop está soportado
            if (!java.awt.Desktop.isDesktopSupported()) {
                System.out.println("[ADMIN] Desktop no soportado en este sistema. Mostrando mapa en consola.");
                printMap();
                return;
            }

            java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
            if (!desktop.isSupported(java.awt.Desktop.Action.OPEN)) {
                System.out.println("[ADMIN] Acción OPEN no soportada. Mostrando mapa en consola.");
                printMap();
                return;
            }

            // Archivo de la imagen
            java.io.File imgFile = new java.io.File(MAP_IMAGE_PATH);
            if (!imgFile.exists()) {
                System.out.println("[ADMIN] Imagen de mapa no encontrada:");
                System.out.println("        " + imgFile.getAbsolutePath());
                System.out.println("        (usa printMap() como referencia por ahora)");
                printMap();
                return;
            }

            // Abrir la imagen con el visor predeterminado del SO
            desktop.open(imgFile);
            System.out.println("[ADMIN] Imagen del mapa abierta: " + imgFile.getAbsolutePath());

        } catch (Exception e) {
            System.out.println("[ADMIN] Error al abrir la imagen del mapa: " + e.getMessage());
            System.out.println("[ADMIN] Mostrando mapa lógico en texto como respaldo.");
            printMap();
        }
    }


    /**
     * Crear enemigo desde la consola del admin.
     * Recibe el evento, el tipo de enemigo y la ubicación lógica.
     */
    public void crearEnemigoComoAdmin(Evento evento, String tipo,  Integer liana, Integer plataforma) {
        GestorJuego gestorEvento = gestores.get(evento);
        if (gestorEvento == null) {
            System.out.println("[ADMIN] No existe GestorJuego para " + evento);
            return;
        }

        Float x;
        Float y;
        String tipoFinal = tipo;

        if ("CROC_BLUE".equalsIgnoreCase(tipo)) {
            LayoutDKJr.LianaDef l = LayoutDKJr.getLiana(liana);
            x = l.x;
            y = LayoutDKJr.getYTopLiana(liana);
            tipoFinal = "CROC_BLUE_LIANA";
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

        gestorEvento.crearEnemigo(tipoFinal, x, y);

        Paquete p = Paquete.crearEnemigo("ADMIN", tipoFinal, x, y);
        notifyByEvento(evento, p);

        System.out.println("[ADMIN] Enemigo " + tipoFinal + " creado en " + evento +
                        " (liana=" + liana + ", plataforma=" + plataforma +
                        ") -> (" + x + ", " + y + ")");
    }

    /**
     * Crear fruta desde la consola del admin.
     */
    public void crearFrutaComoAdmin(Evento evento, Integer liana, Integer alturaIndex, Integer puntos) {
        GestorJuego gestorEvento = gestores.get(evento);
        if (gestorEvento == null) {
            System.out.println("[ADMIN] No existe GestorJuego para " + evento);
            return;
        }

        Float x = LayoutDKJr.getXForLiana(liana);
        Float y = LayoutDKJr.getYOnLiana(liana, alturaIndex);

        // 1) Crear en el gestor de ESTE evento
        gestorEvento.crearFruta(x, y, puntos);

        // 2) Notificar a los clientes de ese evento
        Paquete p = Paquete.crearFruta("ADMIN", x, y, puntos);
        notifyByEvento(evento, p);

        System.out.println("[ADMIN] Fruta creada en " + evento +
                        " (liana=" + liana + ", altura=" + alturaIndex +
                        ", puntos=" + puntos + ") -> (" + x + ", " + y + ")");
    }

    /**
     * Elimina una fruta desde la consola del admin.
     */
    public void eliminarFrutaComoAdmin(Evento evento,  Integer liana, Integer alturaIndex) {
        GestorJuego gestorEvento = gestores.get(evento);
        if (gestorEvento == null) {
            System.out.println("[ADMIN] No existe GestorJuego para " + evento);
            return;
        }

        Float x = LayoutDKJr.getXForLiana(liana);
        Float y = LayoutDKJr.getYOnLiana(liana, alturaIndex);

        // Elimina en el gestor de ESTE evento (usa una tolerancia pequeña en píxeles)
        Boolean ok = gestorEvento.eliminarFrutaPorPosicion(x, y, 5.0f);

        if (ok.booleanValue()) {
            System.out.println("[ADMIN] Fruta ELIMINADA en " + evento +
                    " (liana=" + liana + ", altura=" + alturaIndex +
                    ") -> (" + x + ", " + y + ")");
        } else {
            System.out.println("[ADMIN] No se encontró fruta para eliminar en " + evento +
                    " (liana=" + liana + ", altura=" + alturaIndex +
                    ") -> (" + x + ", " + y + ")");
        }
        // No hace falta mandar paquete especial: el próximo ESTADO_JUEGO ya vendrá sin esa fruta
    }


    /**
     * Inicia un hilo que permite a un usuario administrador crear
     * enemigos y frutas desde la consola del servidor.
     */
    private void startAdminConsole() {
        Thread adminThread = new Thread(() -> {
            Scanner sc = new Scanner(System.in);

            System.out.println("===========================================");
            System.out.println("[ADMIN] Consola de administración iniciada");
            System.out.println("===========================================");

            Boolean seguir = Boolean.TRUE;

            while (seguir.booleanValue()) {
                System.out.println();
                System.out.println("========= MENÚ ADMIN =========");
                System.out.println("  1) Crear enemigo");
                System.out.println("  2) Crear fruta");
                System.out.println("  3) Eliminar fruta");
                System.out.println("  4) Ver mapa lógico (lianas/plataformas)");
                System.out.println("  5) Ayuda / Descripción");
                System.out.println("  0) Salir de consola admin");
                System.out.println("================================");

                Integer opcion = leerEnteroEnRango(sc, "[ADMIN] Opción", 0, 5);

                switch (opcion) {
                    case 0:
                        System.out.println("[ADMIN] Consola de administración finalizada.");
                        seguir = Boolean.FALSE;
                        break;

                    case 1:
                        try {
                            manejarCrearEnemigo(sc);
                        } catch (Exception e) {
                            System.out.println("[ADMIN] Error creando enemigo: " + e.getMessage());
                        }
                        break;

                    case 2:
                        try {
                            manejarCrearFruta(sc);
                        } catch (Exception e) {
                            System.out.println("[ADMIN] Error creando fruta: " + e.getMessage());
                        }
                        break;

                    case 3:
                        try {
                            manejarEliminarFruta(sc);
                        } catch (Exception e) {
                            System.out.println("[ADMIN] Error eliminando fruta: " + e.getMessage());
                        }
                        break;

                    case 4:
                        manejarMostrarMapa();
                        break;

                    case 5:
                        System.out.println("Descripción rápida:");
                        System.out.println("  - Enemigos ROJOS: suben y bajan en una liana o caminan en una plataforma.");
                        System.out.println("  - Enemigos AZULES: bajan por una liana y se caen.");
                        System.out.println("  - Frutas: se crean en liana + altura y dan puntos.");
                        System.out.println("  - Eliminar fruta: usa la misma liana + altura donde fue creada.");
                        break;
                }
            }
        });

        adminThread.setDaemon(Boolean.TRUE); // no impide que el server se cierre si el main termina
        adminThread.start();
    }

    // Imprime el mapa lógico (lianas y plataformas) en la consola
    private void printMap() {
        System.out.println("===========================================");
        System.out.println("              MAPA LÓGICO DK JR           ");
        System.out.println("===========================================");

        // ---- Lianas ----
        try {
            Integer numLianas = LayoutDKJr.getCantidadLianas();
            System.out.println("-- LIANAS (índice -> x aproximada) --");
            for (Integer i = 0; i < numLianas; i++) {
                Float x = LayoutDKJr.getXForLiana(i);
                System.out.printf("  Liana %d -> x = %.1f%n", i, x);
            }
        } catch (Exception e) {
            System.out.println("[WARN] No se pudo obtener lista de lianas desde LayoutDKJr: " + e.getMessage());
        }

        // ---- Plataformas ----
        try {
            Integer numPlataformas = LayoutDKJr.getCantidadPlataformas();
            System.out.println("-- PLATAFORMAS (índice -> y aproximada) --");
            for (Integer i = 0; i < numPlataformas; i++) {
                Float y = LayoutDKJr.getYForPlataforma(i);
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
        Integer port = 8080;
        
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