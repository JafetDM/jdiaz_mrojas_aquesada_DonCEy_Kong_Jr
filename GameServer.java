import java.io.*;
import java.net.*;

// Clase principal que inicia el servidor
public class GameServer {

    private final int port;

    public GameServer(int port) {
        this.port = port;
    }

    // Método que inicia el servidor
    public void start() {
        System.out.println("Iniciando servidor en el puerto " + port + "...");
        try (ServerSocket serverSocket = new ServerSocket(port)) {

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Cliente conectado desde " + clientSocket.getInetAddress());

                // Crear un hilo para manejar al cliente
                ClientHandler handler = new ClientHandler(clientSocket);
                new Thread(handler).start();
            }

        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        GameServer server = new GameServer(8080);
        server.start();
    }
}

