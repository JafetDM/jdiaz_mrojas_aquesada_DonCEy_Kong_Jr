import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable { 
    // implements para intefaz de una clase 
    // utilizar la interfaz Runnable para el metodo run
    // Thread utiliza un Runnable target, por lo que run debe tener esta interfaz

    private final Socket socket; // socket único y no modificable
    private final String playerName;
    private final GameServer server;
    private DataInputStream in;
    private DataOutputStream out;

    // Instanciador
    public ClientHandler(Socket socket, String playerName, GameServer server) {
        this.socket = socket;
        this.playerName = playerName;
        this.server = server;

        // ==============================================================
        // PASO 3: Obtener los InputStream y/o OutputStream del cliente.
        // ==============================================================

        try {
            // crea In y OutStream mas adecuado a nuestras necesidades (socket)
            in = new DataInputStream(socket.getInputStream()); 
            out = new DataOutputStream(socket.getOutputStream());
        
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Obtener el nombre
    public String getPlayerName() {
        return playerName;
    }

    @Override // redefine run con la base de la misma interfaz que Runnable
    public void run() {
        try{
            while(true){
            // ===============================================
            // PASO 4: Leer y escribir datos (binarios) del y al cliente
            // ===============================================

            int movimiento = in.readInt();
            float x = in.readFloat();
            float y = in.readFloat();

            System.out.println("Jugador " + playerName + ": movimiento=" + movimiento + " x=" + x + " y=" + y);


            // Crear paquete y reenviarlo a todos
            Paquete paquete = new Paquete(playerName, x, y, movimiento);
            sendPacket(paquete);
            System.out.println("Paquete enviado al cliente.");
        }
        

        } catch (IOException e) {
            System.out.println(playerName + " se desconectó. Error: " + e.getMessage());

        }

        // ===============================================
        // PASO 5: Cerrar el socket
        // ===============================================

        
        finally {
            server.removeClient(this);
            try { 
                socket.close(); 
            } catch (IOException e){
                System.err.println("Error al cerrar socket: " + e.getMessage());
            }
        }
    }

    // Enviar paquete binario
    public void sendPacket(Paquete paquete) {
        try {
            out.writeFloat(paquete.x);
            out.writeFloat(paquete.y);
            out.writeInt(paquete.movimiento);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    

    // Método donde podrías incluir la lógica del juego
    private String procesarMensaje(String msg) {
        if (msg.equalsIgnoreCase("PING")) {
            return "PONG";
        } else if (msg.equalsIgnoreCase("start")) {
            return "Iniciando partida...";
        } else {
            return "Servidor: recibí tu mensaje -> " + msg;
        }
    }
}



