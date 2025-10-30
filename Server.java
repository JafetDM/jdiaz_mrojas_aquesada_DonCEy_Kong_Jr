import java.io.*;
import java.net.*;

public class Server {
    public static void main(String[] args) {
        final int PORT = 8080;
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Servidor escuchando en el puerto " + PORT + "...");

            Socket clientSocket = serverSocket.accept();
            System.out.println("Cliente conectado desde " + clientSocket.getInetAddress());

            // Streams de entrada/salida binarios
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());
            DataOutputStream out = new DataOutputStream(clientSocket.getOutputStream());

            // 1️⃣ Leer el tamaño del mensaje
            int length = in.readInt(); // Java lee en big-endian (formato de red)
            byte[] data = new byte[length];
            in.readFully(data);

            String mensaje = new String(data, "UTF-8");
            System.out.println("Mensaje del cliente: " + mensaje);

            // 2️⃣ Enviar respuesta
            String respuesta = "Hola desde el servidor Java!";
            byte[] responseData = respuesta.getBytes("UTF-8");
            out.writeInt(responseData.length);
            out.write(responseData);

            clientSocket.close();
            System.out.println("Comunicación finalizada.");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
