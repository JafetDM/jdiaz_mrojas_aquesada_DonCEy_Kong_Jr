#include <stdio.h> // entrada/salida estándar (printf, perror…)
#include <stdlib.h> // utilidades del sistema (malloc, free, exit…)
#include <string.h> // funciones de manejo de strings (strlen, memcpy…)
#include <unistd.h> // funciones POSIX (read, write, close…)
#include <arpa/inet.h>  // socket(), connect() y otras funciones de red (inet_pton, htonl, ntohl…)

#define PORT 8080 // puerto TCP

// struct para el paquete 
struct Paquete {
    int movimiento;
    float x;
    float y;
};

// Funcion auxiliar para convertir flotantes a formato de red. Los datos que se mandan desde C están en Little Endian. Java los lee en Big Endian.

float convertir_a_formato_red(float f){
    uint32_t temp;
    memcpy(&temp, &f, sizeof(float));  // copia los bits de f en temp (que es un int)
    temp = htonl(temp);                // cambia el orden de bytes del int
    memcpy(&f, &temp, sizeof(float));  // escribe los bits invertidos de temp en f
    return f;
}

int main() {
    int sock = 0; // descriptor del socket.
    struct sockaddr_in serv_addr; // estructura que guarda la dirección del servidor.


    // =======================================
    // PASO 1: Crear socket() y configurarlo
    // =======================================

    if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) { // AF_INET: protocolo IPv4. SOCK_STREAM: socket TCP (flujo confiable)
        // socket() Retorna un descriptor de archivo (sock) que se usa para leer/escribir.
        perror("Error al crear socket"); 
        return -1;
    }

    // Definir el tipo de estructura de direccin de servidor (red, puerto)

    serv_addr.sin_family = AF_INET; // sin_family: tipo de red (IPv4)
    serv_addr.sin_port = htons(PORT); // sin_port: puerto en formato network byte order (big-endian).
    // htons() significa host to network short.

    // Cambiar IP según tu red
    if (inet_pton(AF_INET, "127.0.0.1", &serv_addr.sin_addr) <= 0) { // convierte la direccion IP a binario (in_addr)
        
        // Nota: para conocer el IP de wifi de la Mac en Mac, usar el comando: ipconfig getifaddr en0 
        // 192.168.5.150
        // local: 127
        perror("Dirección inválida o no soportada");
        return -1;
    }

    // =======================================
    // PASO 2: Conectar 
    // Solicitar conexion con funcion connect()
    // En esta llamada se facilita la direccion IP del server y el num de servicio

    // Establece la conexión TCP con el servidor.
    // Si el servidor está escuchando, se crea un canal bidireccional. Si no, falla
    // =======================================

    // Conectar
    if (connect(sock, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
        perror("Error en connect");
        return -1;
    }

    printf("Conectando al servidor\n");

    // =======================================
    // PASO 3: Escribir y leer datos

    // Se convierten los datos a formato de red (htonl para int, para floats se usa funcion auxiliar). (A)
    // Luego se envía el mensaje en ese formato (msg_len bytes). (B)

    // Asi el servidor sabe cuanto leer
    // =======================================

    // A) Enviar tamaño del mensaje en formato de red
    
    struct Paquete paquete = {1, 10.f, 3.2};

    printf("Paquete a enviar: mov=%d, x=%.2f, y=%.2f\n", paquete.movimiento, paquete.x, paquete.y);

    paquete.movimiento = htonl(paquete.movimiento); // htonl() = host to network long: convierte el entero (32 bits) a formato de red.
    paquete.y = convertir_a_formato_red(paquete.y); // lo mismo que htonl pero para flotantes
    paquete.x = convertir_a_formato_red(paquete.x);
    //uint32_t net_len = htonl(msg_len); // htonl() = host to network long: convierte el entero (32 bits) a formato de red.
    //write(sock, &net_len, sizeof(net_len));

    // B) Enviar mensaje
    //write(sock, mensaje, msg_len);
    send(sock, &paquete, sizeof(paquete),0);
    printf("Paquete enviado: mov=%d, x=%.2f, y=%.2f\n", paquete.movimiento, paquete.x, paquete.y);


    // C) Leer respuesta
    //uint32_t resp_len;
    //read(sock, &resp_len, sizeof(resp_len)); // Lee los primeros 4 bytes (tamaño del mensaje de respuesta).
    //resp_len = ntohl(resp_len); // netword to host long: convertir a formato local 

    //char *buffer = malloc(resp_len + 1); // Reserva memoria para la respuesta.
    //read(sock, buffer, resp_len); // Lee exactamente resp_len bytes.
    //buffer[resp_len] = '\0'; // terminar string
    //printf("Respuesta del servidor: %s\n", buffer);

    // Esperar una respuesta (del mismo tamaño)
    struct Paquete resp;
    int n = recv(sock, &resp, sizeof(resp), 0);
    if (n > 0) {
        printf("Respuesta recibida: mov=%d, x=%.2f, y=%.2f\n", resp.movimiento, resp.x, resp.y);
    }

    // =======================================
    // PASO 4: Cerrar la comunicación
    // libera memoria y cierra la conexion
    // =======================================

    close(sock); 
    return 0;
}
