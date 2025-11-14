
// cliente juego en C

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <pthread.h>
#include <stdbool.h>
#include <errno.h>

#include "raylib.h"
#include "librerias/cJSON.h"
#include "config.h"

// ========================
// Estructuras de estado
// ========================

typedef struct {
    float x, y;
    int vida;
} Player;

typedef struct {
    float x, y;
    int tipo;
    int estado;
} Enemy;

typedef struct {
    float x, y;
    int tipo;
    int estado;
} Fruit;

typedef struct {
    Player jugador;

    Enemy *enemigos;
    int totalEnemigos;
    int capacityEnemigos;

    Fruit *frutas;
    int totalFrutas;
    int capacityFrutas;

    pthread_mutex_t mutex; // protege este GameState
} GameState;

// ========================
// Globals
// ========================

static GameState g_state;
static int g_sock = -1;
static volatile bool g_running = true;
static pthread_mutex_t g_send_mutex = PTHREAD_MUTEX_INITIALIZER;


// ===========================
// Funciones auxiliares 
// para el manejo de frutas y enemigos struct
// ===========================

static void ensure_enemy_capacity(GameState *s, int needed) {
    if (needed <= s->capacityEnemigos) return;
    int newcap = s->capacityEnemigos ? s->capacityEnemigos * 2 : INITIAL_ENEMIES_CAP;
    while (newcap < needed) newcap *= 2;
    s->enemigos = realloc(s->enemigos, sizeof(Enemy) * newcap);
    s->capacityEnemigos = newcap;
}

static void ensure_fruit_capacity(GameState *s, int needed) {
    if (needed <= s->capacityFrutas) return;
    int newcap = s->capacityFrutas ? s->capacityFrutas * 2 : INITIAL_FRUITS_CAP;
    while (newcap < needed) newcap *= 2;
    s->frutas = realloc(s->frutas, sizeof(Fruit) * newcap);
    s->capacityFrutas = newcap;
}


// Funcion auxiliar para convertir flotantes a formato de red. Los datos que se mandan desde C están en Little Endian. Java los lee en Big Endian.
// NO SE USA 

float convertir_a_formato_red(float f){
    uint32_t temp;
    memcpy(&temp, &f, sizeof(float));  // copia los bits de f en temp (que es un int)
    temp = htonl(temp);                // cambia el orden de bytes del int
    memcpy(&f, &temp, sizeof(float));  // escribe los bits invertidos de temp en f
    return f;
}

// -------------------------
// Parse JSON -> GameState (thread-safe if caller locks)
// -------------------------
static void parse_json_game_state_locked(GameState *s, const char *jsonText) {
    if (!jsonText) return;
    cJSON *root = cJSON_Parse(jsonText);
    if (!root) {
        // parse error
        return;
    }

    // jugador
    cJSON *jug = cJSON_GetObjectItem(root, "jugador");
    if (jug) {
        cJSON *jx = cJSON_GetObjectItem(jug, "x");
        cJSON *jy = cJSON_GetObjectItem(jug, "y");
        cJSON *jv = cJSON_GetObjectItem(jug, "vida");
        if (jx) s->jugador.x = (float)jx->valuedouble;
        if (jy) s->jugador.y = (float)jy->valuedouble;
        if (jv) s->jugador.vida = jv->valueint;
    }

    // enemigos
    cJSON *arrEn = cJSON_GetObjectItem(root, "enemigos");
    if (arrEn && cJSON_IsArray(arrEn)) {
        int n = cJSON_GetArraySize(arrEn);
        ensure_enemy_capacity(s, n);
        s->totalEnemigos = n;
        for (int i = 0; i < n; i++) {
            cJSON *e = cJSON_GetArrayItem(arrEn, i);
            if (!e) continue;
            cJSON *ex = cJSON_GetObjectItem(e, "x");
            cJSON *ey = cJSON_GetObjectItem(e, "y");
            cJSON *et = cJSON_GetObjectItem(e, "tipo");
            cJSON *es = cJSON_GetObjectItem(e, "estado");
            s->enemigos[i].x = ex ? (float)ex->valuedouble : 0;
            s->enemigos[i].y = ey ? (float)ey->valuedouble : 0;
            s->enemigos[i].tipo = et ? et->valueint : 0;
            s->enemigos[i].estado = es ? es->valueint : 0;
        }
    } else {
        s->totalEnemigos = 0;
    }

    // frutas
    cJSON *arrFr = cJSON_GetObjectItem(root, "frutas");
    if (arrFr && cJSON_IsArray(arrFr)) {
        int n = cJSON_GetArraySize(arrFr);
        ensure_fruit_capacity(s, n);
        s->totalFrutas = n;
        for (int i = 0; i < n; i++) {
            cJSON *f = cJSON_GetArrayItem(arrFr, i);
            if (!f) continue;
            cJSON *fx = cJSON_GetObjectItem(f, "x");
            cJSON *fy = cJSON_GetObjectItem(f, "y");
            cJSON *ft = cJSON_GetObjectItem(f, "tipo");
            cJSON *fs = cJSON_GetObjectItem(f, "estado");
            s->frutas[i].x = fx ? (float)fx->valuedouble : 0;
            s->frutas[i].y = fy ? (float)fy->valuedouble : 0;
            s->frutas[i].tipo = ft ? ft->valueint : 0;
            s->frutas[i].estado = fs ? fs->valueint : 0;
        }
    } else {
        s->totalFrutas = 0;
    }

    cJSON_Delete(root);
}

// -------------------------
// Network: send JSON (thread-safe send)
// -------------------------
static int send_json_threadsafe(int sock, const char *jsonText) {
    if (!jsonText) return -1;
    size_t len = strlen(jsonText);
    // we'll send json + newline as delimiter
    pthread_mutex_lock(&g_send_mutex);
    ssize_t w1 = send(sock, jsonText, len, 0);
    ssize_t w2 = send(sock, "\n", 1, 0);
    pthread_mutex_unlock(&g_send_mutex);
    if (w1 < 0 || w2 < 0) return -1;
    return 0;
}

// -------------------------
// Network thread: receive stream, split on '\n', parse
// -------------------------
static void *network_thread(void *arg) {
    int sock = *((int*)arg);
    char buffer[RECV_BUFFER];
    int buf_used = 0;

    while (g_running) {
        ssize_t n = recv(sock, buffer + buf_used, sizeof(buffer) - buf_used - 1, 0);
        if (n > 0) {
            buf_used += (int)n;
            buffer[buf_used] = '\0';

            // extract lines ended with '\n'
            char *line_start = buffer;
            char *newline;
            while ((newline = memchr(line_start, '\n', buffer + buf_used - (unsigned long)(line_start - buffer)))) {
                size_t linelen = (size_t)(newline - line_start);
                char *line = malloc(linelen + 1);
                memcpy(line, line_start, linelen);
                line[linelen] = '\0';

                // parse JSON line -> update GameState (lock)
                pthread_mutex_lock(&g_state.mutex);
                parse_json_game_state_locked(&g_state, line);
                pthread_mutex_unlock(&g_state.mutex);

                free(line);
                line_start = newline + 1;
            }

            // move remaining bytes to beginning
            size_t remaining = buffer + buf_used - line_start;
            memmove(buffer, line_start, remaining);
            buf_used = (int)remaining;
        } else if (n == 0) {
            // server closed
            printf("[network] server closed connection\n");
            g_running = false;
            break;
        } else {
            if (errno == EINTR) continue;
            if (errno == EWOULDBLOCK || errno == EAGAIN) {
                // no data available, continue
                usleep(1000);
                continue;
            }
            perror("[network] recv");
            g_running = false;
            break;
        }
    }

    return NULL;
}

// -------------------------
// Build input JSON (from keyboard) and send
// -------------------------
static void send_input_from_keys(int sock) {
    // Example: send movement vector and action flags
    float dx = 0.0f, dy = 0.0f;
    if (IsKeyDown(KEY_RIGHT)) dx += 1.0f;
    if (IsKeyDown(KEY_LEFT))  dx -= 1.0f;
    if (IsKeyDown(KEY_UP))    dy -= 1.0f;
    if (IsKeyDown(KEY_DOWN))  dy += 1.0f;

    // Only send if movement exists (reduce traffic)
    if (dx == 0.0f && dy == 0.0f) return;

    cJSON *root = cJSON_CreateObject();
    // Could include player id or other metadata if needed
    cJSON_AddNumberToObject(root, "movX", dx);
    cJSON_AddNumberToObject(root, "movY", dy);

    char *json = cJSON_PrintUnformatted(root);
    if (json) {
        send_json_threadsafe(sock, json); // adds newline
        free(json);
    }
    cJSON_Delete(root);
}

// -------------------------
// Render function (reads game state with mutex)
// -------------------------
static void render_game_locked(GameState *s, Texture2D playerTex, Texture2D stageTex) {
    BeginDrawing();
    ClearBackground(RAYWHITE);

    // Draw stage if exists
    if (stageTex.id != 0) {
        Rectangle src = {0, 0, (float)stageTex.width, (float)stageTex.height};
        Rectangle dst = {0, 0, (float)SCREEN_WIDTH, (float)SCREEN_HEIGHT};
        DrawTexturePro(stageTex, src, dst, (Vector2){0,0}, 0.0f, WHITE);
    }

    // player
    DrawCircle(s->jugador.x, s->jugador.y, 12, BLUE);

    // enemies
    for (int i = 0; i < s->totalEnemigos; i++) {
        Color col = RED;
        DrawCircle(s->enemigos[i].x, s->enemigos[i].y, 10, col);
    }

    // fruits
    for (int i = 0; i < s->totalFrutas; i++) {
        DrawCircle(s->frutas[i].x, s->frutas[i].y, 8, GREEN);
    }

    // grid
    for (int x = 0; x < SCREEN_WIDTH; x += 32)
        DrawLine(x, 0, x, SCREEN_HEIGHT, Fade(GREEN, 0.15f));
    for (int y = 0; y < SCREEN_HEIGHT; y += 32)
        DrawLine(0, y, SCREEN_WIDTH, y, Fade(GREEN, 0.15f));

    EndDrawing();
}
int main() {
    // init GameState
    memset(&g_state, 0, sizeof(GameState));
    pthread_mutex_init(&g_state.mutex, NULL);
    g_state.enemigos = NULL;
    g_state.frutas = NULL;
    g_state.capacityEnemigos = 0;
    g_state.capacityFrutas = 0;
    g_state.totalEnemigos = 0;
    g_state.totalFrutas = 0;
    g_state.jugador.x = SCREEN_WIDTH/2.0f;
    g_state.jugador.y = SCREEN_HEIGHT-50.0f;
    g_state.jugador.vida = 3;

    struct sockaddr_in serv_addr; // estructura que guarda la dirección del servidor.


    // =======================================
    // PASO 1: Crear socket() y configurarlo
    // =======================================

    g_sock = socket(AF_INET, SOCK_STREAM, 0);
    if (g_sock < 0) { // AF_INET: protocolo IPv4. SOCK_STREAM: socket TCP (flujo confiable)
        // socket() Retorna un descriptor de archivo (sock) que se usa para leer/escribir.
        perror("Error al crear socket"); 
        return -1;
    }

    // Definir el tipo de estructura de direccin de servidor (red, puerto)

    serv_addr.sin_family = AF_INET; // sin_family: tipo de red (IPv4)
    serv_addr.sin_port = htons(SERVER_PORT); // sin_port: puerto en formato network byte order (big-endian).
    // htons() significa host to network short.

    // Cambiar IP según tu red
    if (inet_pton(AF_INET, SERVER_IP, &serv_addr.sin_addr) <= 0) { // convierte la direccion IP a binario (in_addr)
        perror("Dirección inválida o no soportada");
        close(g_sock);
        return 1;
    }

    // =======================================
    // PASO 2: Conectar 
    // Solicitar conexion con funcion connect()
    // En esta llamada se facilita la direccion IP del server y el num de servicio

    // Establece la conexión TCP con el servidor.
    // Si el servidor está escuchando, se crea un canal bidireccional. Si no, falla
    // =======================================

    // Conectar
    if (connect(g_sock, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
        perror("Error en connect");
        close(g_sock)
        return 1;
    }

    printf("[main] connected to %s:%d\n", SERVER_IP, SERVER_PORT);

    // =======================================
    // PASO 3: Escribir y leer datos

    // Se convierten los datos a formato de red (htonl para int, para floats se usa funcion auxiliar). (A)
    // Luego se envía el mensaje en ese formato (msg_len bytes). (B)

    // Asi el servidor sabe cuanto leer
    // =======================================

    // ---------- start network thread ----------
    pthread_t net_thread;
    if (pthread_create(&net_thread, NULL, network_thread, &g_sock) != 0) {
        perror("pthread_create");
        close(g_sock);
        return 1;
    }

    // ---------- init raylib ----------
    InitWindow(SCREEN_WIDTH, SCREEN_HEIGHT, "Cliente - JSON + Raylib");
    SetTargetFPS(TARGET_FPS);

    Texture2D playerTex = {0};
    Texture2D stageTex = {0};
    // Try to load assets if present
    if (FileExists(PLAYER_TEXTURE_PATH)) playerTex = LoadTexture(PLAYER_TEXTURE_PATH);
    if (FileExists(STAGE_TEXTURE_PATH)) stageTex = LoadTexture(STAGE_TEXTURE_PATH);

    // main loop
    while (!WindowShouldClose() && g_running) {

        // handle input and send (non-blocking send is guarded by mutex)
        send_input_from_keys(g_sock);

        // draw current state: lock while reading
        pthread_mutex_lock(&g_state.mutex);
        render_game_locked(&g_state, playerTex, stageTex);
        pthread_mutex_unlock(&g_state.mutex);
    }

    // shutdown
    g_running = false;
    shutdown(g_sock, SHUT_RDWR);
    pthread_join(net_thread, NULL);

    // cleanup
    if (playerTex.id != 0) UnloadTexture(playerTex);
    if (stageTex.id != 0) UnloadTexture(stageTex);
    CloseWindow();

    // free dynamic arrays
    free(g_state.enemigos);
    free(g_state.frutas);
    pthread_mutex_destroy(&g_state.mutex);

    close(g_sock);
    return 0;
}
