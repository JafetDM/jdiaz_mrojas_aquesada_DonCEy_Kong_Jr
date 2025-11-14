package serverJava;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

// Clase principal que inicia el servidor

// ============
// Publisher
// ============

public class GameServer {

    // Puerto
    private final int port;

    // Lista de todos los clientes conectados
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>();

    // Gestor que lleva enemigos, frutas, etc.
    private final GestorJuego gestor;

    // Instanciador del server
    public GameServer(int port) {
        this.port = port;
        // aquí metemos la fábrica que hicimos
        this.gestor = new GestorJuego(new GestorJuego.FabricaDKJr());
    }

    // Método que inicia el servidor
    public void start() {
        System.out.println("Iniciando servidor en el puerto " + port + "...");

        // Hilo que actualiza el mundo del juego (enemigos que suben/bajan)
        startGameLoop();

        // PASO 1: Crear/inicializar el socket servidor con un puerto específico
        try (ServerSocket serverSocket = new ServerSocket(port)) {

            int playerCount = 0; // Contador para asignar nombres

            while (true) { // mantiene el server activo

                // PASO 2: Aceptar el cliente
                Socket clientSocket = serverSocket.accept();
                playerCount++;
                String playerName = "Jugador" + playerCount;
                System.out.println(playerName + " conectado desde " + clientSocket.getInetAddress());

                // Crear un hilo para manejar al cliente
                ClientHandler handler = new ClientHandler(clientSocket, playerName, this);
                clients.add(handler);

                new Thread(handler).start(); // inicia un thread
            }

        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // bucle de juego simple: actualiza el GestorJuego a ~60 FPS
    private void startGameLoop() {
        Thread gameThread = new Thread(() -> {
            final float dt = 1.0f / 60.0f; // 60 FPS
            while (true) {
                try {
                    gestor.actualizar(dt);
                    // aquí más adelante puedes hacer: enviar estado a todos los clientes
                    Thread.sleep(16); // ~60fps
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        gameThread.setDaemon(true);
        gameThread.start();
    }


    public void processPlayerInput(ClientHandler sender, Paquete paquete) {

    // Aquí puedes aplicar lógica del juego si quieres:
    // mover jugador, validar posiciones, colisiones, etc.
    
    // Por ahora solo retransmitimos a todos
    sendAll(paquete);
}

    // Enviar un mensaje a todos los clientes (lo que ya tenías)
    public void sendAll(Paquete paquete) {
        String json = paquete.toJson();
        for (ClientHandler client : clients) {
            client.sendJson(json);
        }
    }


    // Eliminar un cliente si se desconecta
    public void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println(client.getPlayerName() + " se ha desconectado.");
    }

    // ========= métodos nuevos para que el handler pueda crear cosas ========

    // usado por un cliente "master" para crear un cocodrilo
    public void crearEnemigo(String tipo, float x, float y) {
        gestor.crearEnemigo(tipo, x, y);
    }

    // usado por un cliente "master" para crear frutas
    public void crearFruta(float x, float y, int puntos) {
        gestor.crearFruta(x, y, puntos);
    }

    // si algún handler quiere leer el estado completo
    public List<ElementoJuego> obtenerElementos() {
        return gestor.obtenerElementos();
    }

    // Iniciar el server
    public static void main(String[] args) {
        GameServer server = new GameServer(8080); // instancia el server en el puerto 8080
        server.start(); // inicia el server
    }
}
