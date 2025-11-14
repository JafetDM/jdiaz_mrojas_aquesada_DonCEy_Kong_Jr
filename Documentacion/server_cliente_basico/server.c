#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>         // close(), read(), write()
#include <arpa/inet.h>      // socket(), bind(), listen(), accept()

#define PORT 8080

int main() {
    int server_fd, new_socket;
    struct sockaddr_in address;
    int addrlen = sizeof(address);
    char buffer[1024] = {0};
    char *mensaje = "Hola cliente, soy el servidor!\n";

    // 1️⃣ Crear el socket
    server_fd = socket(AF_INET, SOCK_STREAM, 0);
    if (server_fd == -1) {
        perror("Error al crear socket");
        exit(EXIT_FAILURE);
    }

    // 2️⃣ Asociar (bind) el socket a una IP y puerto
    address.sin_family = AF_INET;
    address.sin_addr.s_addr = INADDR_ANY; // Escuchar en todas las interfaces
    address.sin_port = htons(PORT);

    if (bind(server_fd, (struct sockaddr *)&address, sizeof(address)) < 0) {
        perror("Error en bind");
        close(server_fd);
        exit(EXIT_FAILURE);
    }

    // 3️⃣ Poner el socket en modo escucha
    if (listen(server_fd, 3) < 0) {
        perror("Error en listen");
        close(server_fd);
        exit(EXIT_FAILURE);
    }

    printf("Servidor escuchando en el puerto %d...\n", PORT);

    // 4️⃣ Esperar y aceptar conexiones
    new_socket = accept(server_fd, (struct sockaddr *)&address, (socklen_t*)&addrlen);
    if (new_socket < 0) {
        perror("Error en accept");
        close(server_fd);
        exit(EXIT_FAILURE);
    }

    printf("Cliente conectado!\n");

    // 5️⃣ Comunicación: recibir y enviar datos
    read(new_socket, buffer, 1024);
    printf("Mensaje del cliente: %s\n", buffer);

    write(new_socket, mensaje, strlen(mensaje));

    // 6️⃣ Cerrar sockets
    close(new_socket);
    close(server_fd);

    return 0;
}
