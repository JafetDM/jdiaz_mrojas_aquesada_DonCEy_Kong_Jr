package serverJava;
import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable, Subscriber { 
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

    @Override
    public void update(Paquete state) {
        // Enviar el estado actualizado al cliente
        try {
            out.writeUTF(state.toJson());
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    @Override // redefine run con la base de la misma interfaz que Runnable
    public void run() {
        try{
            while(true){
            // ===============================================
            // PASO 4: Leer y escribir datos (JSON) del y al cliente
            // ===============================================

            String jsonInput = in.readUTF();

            // Convertir JSON a objeto paquete

            Paquete paquete = Paquete.fromJson(jsonInput);

            System.out.println("Jugador " + playerName + 
                                ": movimiento=" + paquete.movimiento +
                                " x=" + paquete.x + " y=" + paquete.y);


            // Crear paquete y reenviarlo a todos
            server.processPlayerInput(this, paquete);

            System.out.println("Paquete enviado al cliente.");
        }

        // excepcion si cliente se desconecta
        

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

    // Enviar paquete JSON
    public void sendPacket(Paquete paquete) {
        try {
            out.writeUTF(paquete.toJson());
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

    // Metodo para enviar JSON
    public void sendJson(String json) {
    try {
        out.writeUTF(json);
        out.flush();
    } catch (IOException e) {
        e.printStackTrace();
    }
}

}



