#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>  // socket(), connect()

#define PORT 8080

int main() {
    int sock = 0;
    struct sockaddr_in serv_addr;
    char *mensaje = "Hola desde el cliente en C!";
    uint32_t msg_len = strlen(mensaje);

    // Crear socket
    if ((sock = socket(AF_INET, SOCK_STREAM, 0)) < 0) {
        perror("Error al crear socket");
        return -1;
    }

    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(PORT);

    // Cambiar IP según tu red
    if (inet_pton(AF_INET, "192.168.5.150", &serv_addr.sin_addr) <= 0) {
        // ipconfig getifaddr en0 
        // usar el comando de arriba para obtener IP en mac
        perror("Dirección inválida o no soportada");
        return -1;
    }

    // Conectar
    if (connect(sock, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
        perror("Error en connect");
        return -1;
    }

    // 1️⃣ Enviar tamaño del mensaje en formato de red
    uint32_t net_len = htonl(msg_len);
    write(sock, &net_len, sizeof(net_len));

    // 2️⃣ Enviar mensaje
    write(sock, mensaje, msg_len);

    // 3️⃣ Leer respuesta
    uint32_t resp_len;
    read(sock, &resp_len, sizeof(resp_len));
    resp_len = ntohl(resp_len); // convertir a formato local

    char *buffer = malloc(resp_len + 1);
    read(sock, buffer, resp_len);
    buffer[resp_len] = '\0';

    printf("Respuesta del servidor: %s\n", buffer);

    free(buffer);
    close(sock);
    return 0;
}
