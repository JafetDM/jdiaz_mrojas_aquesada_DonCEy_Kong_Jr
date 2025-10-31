import java.io.*;
import java.net.*;

// Clase principal que inicia el servidor
public class GameServer {

    private final int port; // private final: accesible solo por, no puede cambiar una vez asignado

    // Instanciador del server
    public GameServer(int port) {
        this.port = port;
    }

    // Método que inicia el servidor
    public void start() {
        System.out.println("Iniciando servidor en el puerto " + port + "..."); 
        
        // PASO 1: Crear/inicializar el socket servidor con un puerto específico
        try (ServerSocket serverSocket = new ServerSocket(port)) { 

            while (true) { // mantiene el server activo 
                // PASO 2: Aceptar el cliente
                Socket clientSocket = serverSocket.accept(); 
                System.out.println("Cliente conectado desde " + clientSocket.getInetAddress());

                // Crear un hilo para manejar al cliente
                ClientHandler handler = new ClientHandler(clientSocket); //usa la clase clientHandler propia
                new Thread(handler).start(); // inicia un threat
            }

        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        GameServer server = new GameServer(8080); // instancia el server en el puerto 8080
        server.start(); // inicia el server
    }
}

