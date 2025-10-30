#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>  // socket(), connect()

#define PORT 8080

int main() {
    int sock = 0;
    struct sockaddr_in serv_addr;
    char buffer[1024] = {0};
    char *mensaje = "Hola servidor, soy el cliente!";

    // 1️⃣ Crear el socket
    sock = socket(AF_INET, SOCK_STREAM, 0);
    if (sock < 0) {
        perror("Error al crear socket");
        exit(EXIT_FAILURE);
    }

    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(PORT);

    // 2️⃣ Convertir dirección IP de texto a binario
    if (inet_pton(AF_INET, "127.0.0.1", &serv_addr.sin_addr) <= 0) { 
        // ipconfig getifaddr en0 
        // usar el comando de arriba para obtener IP en mac

        perror("Dirección inválida o no soportada");
        exit(EXIT_FAILURE);
    }

    // 3️⃣ Conectar al servidor
    if (connect(sock, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
        perror("Error en connect");
        exit(EXIT_FAILURE);
    }

    // 4️⃣ Enviar y recibir datos
    write(sock, mensaje, strlen(mensaje));
    read(sock, buffer, 1024);
    printf("Mensaje del servidor: %s\n", buffer);

    // 5️⃣ Cerrar conexión
    close(sock);

    return 0;
}
