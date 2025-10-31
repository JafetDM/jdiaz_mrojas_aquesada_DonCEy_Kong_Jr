import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable { 
    // implements para intefaz de una clase 
    // utilizar la interfaz Runnable para el metodo run
    // Thread utiliza un Runnable target, por lo que run debe tener esta interfaz

    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override // redefine run con la base de la misma interfaz que Runnable
    public void run() {
        // ==============================================================
        // PASO 3: Obtener los InputStream y/o OutputStream del cliente.
        // ==============================================================

        try (
            // crea In y OutStream mas adecuado a nuestras necesidades (socket)
            DataInputStream in = new DataInputStream(socket.getInputStream()); 
            DataOutputStream out = new DataOutputStream(socket.getOutputStream())
        ) {

            // ===============================================
            // PASO 4: Leer y escribir datos del y al cliente
            // ===============================================
            
            //Leer tamaño del mensaje
            int length = in.readInt(); 
            byte[] data = new byte[length];
            in.readFully(data);

            String mensaje = new String(data, "UTF-8");
            System.out.println("Mensaje recibido: " + mensaje);

            // Procesar mensaje (puedes agregar lógica del juego aquí)
            String respuesta = procesarMensaje(mensaje);

            // Enviar respuesta
            byte[] responseData = respuesta.getBytes("UTF-8");
            out.writeInt(responseData.length);
            out.write(responseData);

            System.out.println("Respuesta enviada al cliente.");

        } catch (IOException e) {
            System.err.println("Error con el cliente: " + e.getMessage());
        } finally {

            // ===============================================
            // PASO 5: Cerrar el socket
            // ===============================================
            try {
                socket.close();
            } catch (IOException e) {
                System.err.println("Error al cerrar socket: " + e.getMessage());
            }
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

