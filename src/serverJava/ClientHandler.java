package serverJava;

import java.io.*;
import java.net.*;

/**
 * ClientHandler - Subscriber en el patrón Observer
 * Maneja la comunicación con un cliente específico
 * y recibe actualizaciones del servidor
 */
public class ClientHandler implements Runnable, Subscriber {
    
    private final Socket socket;
    private final String playerName;
    private final Evento evento;  // NUEVO: Evento al que pertenece
    private final GameServer server;
    private DataInputStream in;
    private DataOutputStream out;
    private volatile boolean running = true;
    
    public ClientHandler(Socket socket, String playerName, Evento evento, GameServer server) {
        this.socket = socket;
        this.playerName = playerName;
        this.evento = evento;  // NUEVO
        this.server = server;
        
        try {
            // Configurar streams
            in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
            out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
            
            System.out.println("[*] Streams configurados para " + playerName);
            
        } catch (IOException e) {
            System.err.println("Error al crear streams para " + playerName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public String getPlayerName() {
        return playerName;
    }
    
    public Evento getEvento() {  // NUEVO
        return evento;
    }
    
    // =============== IMPLEMENTACIÓN DE SUBSCRIBER ===============
    
    /**
     * Método del patrón Observer
     * Es llamado por el servidor cuando hay una actualización
     * @param paquete El paquete con los datos actualizados
     */
    @Override
    public void update(Paquete paquete) {
        if (paquete != null && paquete.isValid()) {
            // Serializar el paquete a JSON usando Gson
            String json = paquete.toJson();
            sendJson(json);
        } else {
            System.err.println("[ERROR] Intento de enviar paquete inválido a " + playerName);
        }
    }
    
    // =============== IMPLEMENTACIÓN DE RUNNABLE ===============
    
    @Override
    public void run() {
        System.out.println("[*] Thread iniciado para " + playerName);
        
        try {
            while (running) {
                // Leer JSON del cliente
                String jsonInput = in.readUTF();
                
                if (jsonInput == null || jsonInput.trim().isEmpty()) {
                    System.out.println("[!] Mensaje vacío de " + playerName);
                    continue;
                }
                
                // Deserializar JSON a Paquete
                Paquete paquete = Paquete.fromJson(jsonInput);
                
                if (paquete == null) {
                    System.err.println("[ERROR] No se pudo parsear JSON de " + playerName);
                    continue;
                }
                
                // Agregar el nombre del jugador si no viene en el paquete
                if (paquete.playerName == null || paquete.playerName.isEmpty()) {
                    paquete.playerName = playerName;
                }
                
                System.out.println("[<-] " + playerName + " enviĂł: " + paquete);
                
                // Procesar el paquete en el servidor
                server.processPlayerInput(this, paquete);
            }
            
        } catch (EOFException e) {
            System.out.println("[!] " + playerName + " cerró la conexión");
            
        } catch (IOException e) {
            if (running) {
                System.err.println("[ERROR] IOException en " + playerName + ": " + e.getMessage());
            }
            
        } catch (Exception e) {
            System.err.println("[ERROR] Excepción inesperada en " + playerName + ": " + e.getMessage());
            e.printStackTrace();
            
        } finally {
            cleanup();
        }
    }
    
    // =============== MÉTODOS DE ENVÍO ===============
    
    /**
     * Envía un paquete al cliente (serializa con Gson)
     * @param paquete El paquete a enviar
     */
    public void sendPacket(Paquete paquete) {
        if (paquete != null && paquete.isValid()) {
            String json = paquete.toJson();
            sendJson(json);
        } else {
            System.err.println("[ERROR] Intento de enviar paquete inválido a " + playerName);
        }
    }
    
    /**
     * Envía un String JSON al cliente (thread-safe)
     * @param json El JSON a enviar
     */
    public synchronized void sendJson(String json) {
        if (!running || out == null) {
            return;
        }
        
        // Validar JSON antes de enviar
        if (json == null || json.trim().isEmpty()) {
            System.err.println("[ERROR] Intento de enviar JSON vacío a " + playerName);
            return;
        }
        
        // Opcional: validar que sea JSON válido
        if (!JsonUtils.isValidJson(json)) {
            System.err.println("[ERROR] Intento de enviar JSON inválido a " + playerName);
            System.err.println("JSON: " + json);
            return;
        }
        
        try {
            out.writeUTF(json);
            out.flush();
            // Log reducido - solo para tipos importantes
            // System.out.println("[->] Enviado a " + playerName);
            
        } catch (IOException e) {
            System.err.println("[ERROR] No se pudo enviar a " + playerName + ": " + e.getMessage());
            running = false;
        }
    }
    
    // =============== LIMPIEZA Y CIERRE ===============
    
    /**
     * Detiene el handler y limpia recursos
     */
    public void stop() {
        running = false;
        cleanup();
    }
    
    /**
     * Limpia recursos y cierra conexiones
     */
    private void cleanup() {
        running = false;
        
        // Remover del servidor (esto también cancela la suscripción)
        server.removeClient(this);
        
        // Cerrar streams
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
            System.out.println("[*] Recursos liberados para " + playerName);
            
        } catch (IOException e) {
            System.err.println("[ERROR] Error al cerrar recursos de " + playerName + ": " + e.getMessage());
        }
    }
    
    // =============== MÉTODOS DE UTILIDAD (LEGACY) ===============
    
    /**
     * Procesa mensajes de texto simple
     * @deprecated Usar sistema de paquetes JSON en su lugar
     */
    @Deprecated
    private String procesarMensaje(String msg) {
        if (msg.equalsIgnoreCase("PING")) {
            return "PONG";
        } else if (msg.equalsIgnoreCase("start")) {
            return "Iniciando partida...";
        } else {
            return "Servidor: recibĂ­ tu mensaje -> " + msg;
        }
    }
    
    @Override
    public String toString() {
        return "ClientHandler{" +
                "playerName='" + playerName + '\'' +
                ", evento=" + evento +
                ", socket=" + socket.getRemoteSocketAddress() +
                ", running=" + running +
                '}';
    }
}