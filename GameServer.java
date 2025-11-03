import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

// Clase principal que inicia el servidor
public class GameServer {
    // Puerto privado e inmodificable (final)
    private final int port; 
    // Lista de todos los clientes conectados
    private final List<ClientHandler> clients = new CopyOnWriteArrayList<>(); // 

    // Instanciador del server
    public GameServer(int port) {
        this.port = port;
    }

    // Método que inicia el servidor
    public void start() {
        System.out.println("Iniciando servidor en el puerto " + port + "..."); 
        
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
                
                new Thread(handler).start(); // inicia un threat
            }

        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Enviar un mensaje a todos los clientes
    public void sendAll(Paquete paquete) {
        for (ClientHandler client : clients) {
            client.sendPacket(paquete);
        }
    }

    // Eliminar un cliente si se desconecta
    public void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println(client.getPlayerName() + " se ha desconectado.");
    }

    // Iniciar el server
    public static void main(String[] args) {
        GameServer server = new GameServer(8080); // instancia el server en el puerto 8080
        server.start(); // inicia el server
    }

}
