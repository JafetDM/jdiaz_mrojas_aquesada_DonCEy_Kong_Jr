import java.io.*;
import java.net.*;

public class ClientHandler implements Runnable {

    private final Socket socket;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream())
        ) {
            // 1️⃣ Leer tamaño del mensaje
            int length = in.readInt();
            byte[] data = new byte[length];
            in.readFully(data);

            String mensaje = new String(data, "UTF-8");
            System.out.println("Mensaje recibido: " + mensaje);

            // 2️⃣ Procesar mensaje (puedes agregar lógica del juego aquí)
            String respuesta = procesarMensaje(mensaje);

            // 3️⃣ Enviar respuesta
            byte[] responseData = respuesta.getBytes("UTF-8");
            out.writeInt(responseData.length);
            out.write(responseData);

            System.out.println("Respuesta enviada al cliente.");

        } catch (IOException e) {
            System.err.println("Error con el cliente: " + e.getMessage());
        } finally {
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

