#include <stdio.h> // entrada/salida estándar (printf, perror…)
#include <stdlib.h> // utilidades del sistema (malloc, free, exit…)
#include <string.h> // funciones de manejo de strings (strlen, memcpy…)
#include <unistd.h> // funciones POSIX (read, write, close…)
#include <arpa/inet.h>  // socket(), connect() y otras funciones de red (inet_pton, htonl, ntohl…)

#include "librerias/cJSON.h" // parsing de JSON

#define PORT 8080 // puerto TCP

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
    
    // No se necesita porque usamos JSON

    // B) Enviar mensaje

    cJSON *root = cJSON_CreateObject();
    cJSON_AddNumberToObject(root, "movimiento", 1);
    cJSON_AddNumberToObject(root, "x", 10.0f);
    cJSON_AddNumberToObject(root, "y", 3.2f);

    char *jsonText = cJSON_PrintUnformatted(root);

    send(sock, jsonText, strlen(jsonText), 0);

    printf("JSON enviado:\n%s\n", jsonText);

    cJSON_Delete(root);
    free(jsonText);

    // C) Leer respuesta

    // Esperar una respuesta (del mismo tamaño)
    char buffer[4096];
    int n = recv(sock, buffer, sizeof(buffer) - 1, 0);
    buffer[n] = '\0';

    printf("JSON recibido:\n%s\n", buffer);


    // =======================================
    // PASO 4: Cerrar la comunicación
    // libera memoria y cierra la conexion
    // =======================================

    close(sock); 
    return 0;
}
