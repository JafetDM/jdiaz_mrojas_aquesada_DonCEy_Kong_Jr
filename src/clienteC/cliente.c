// cliente.c - Adaptado para comunicarse con servidor Java usando Paquete y GameState

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
    char playerName[64];
    float x, y;
    int vida;
} Player;

typedef struct {
    char id[32];
    char tipo[32];
    float x, y;
    float velocidad;
    char direccion[16];
} Enemy;

typedef struct {
    char id[32];
    float x, y;
    int puntos;
    bool recolectada;
} Fruit;

typedef struct {
    char evento[32];        // "JUEGO_1" o "JUEGO_2"
    long timestamp;
    
    // Map de jugadores: playerName -> {x, y}
    Player *jugadores;
    int totalJugadores;
    int capacityJugadores;
    
    Enemy *enemigos;
    int totalEnemigos;
    int capacityEnemigos;
    
    Fruit *frutas;
    int totalFrutas;
    int capacityFrutas;
    
    pthread_mutex_t mutex;
} GameState;

// ========================
// Forward declarations (declaraciones adelantadas)
// ========================
static void parse_game_state_json(const char *jsonText);
static void parse_paquete_json(const char *jsonText);

static GameState g_state;
static int g_sock = -1;
static volatile bool g_running = true;
static pthread_mutex_t g_send_mutex = PTHREAD_MUTEX_INITIALIZER;
static char g_playerName[64] = "ClienteC";
static char g_eventoAsignado[32] = "";

// ===========================
// Funciones auxiliares para arrays dinámicos
// ===========================

static void ensure_player_capacity(GameState *s, int needed) {
    if (needed <= s->capacityJugadores) return;
    int newcap = s->capacityJugadores ? s->capacityJugadores * 2 : 10;
    while (newcap < needed) newcap *= 2;
    s->jugadores = realloc(s->jugadores, sizeof(Player) * newcap);
    s->capacityJugadores = newcap;
}

static void ensure_enemy_capacity(GameState *s, int needed) {
    if (needed <= s->capacityEnemigos) return;
    int newcap = s->capacityEnemigos ? s->capacityEnemigos * 2 : 10;
    while (newcap < needed) newcap *= 2;
    s->enemigos = realloc(s->enemigos, sizeof(Enemy) * newcap);
    s->capacityEnemigos = newcap;
}

static void ensure_fruit_capacity(GameState *s, int needed) {
    if (needed <= s->capacityFrutas) return;
    int newcap = s->capacityFrutas ? s->capacityFrutas * 2 : 10;
    while (newcap < needed) newcap *= 2;
    s->frutas = realloc(s->frutas, sizeof(Fruit) * newcap);
    s->capacityFrutas = newcap;
}

// -------------------------
// Parse JSON Paquete del servidor
// -------------------------
static void parse_paquete_json(const char *jsonText) {
    if (!jsonText) return;
    
    cJSON *root = cJSON_Parse(jsonText);
    if (!root) {
        printf("[parse] Error parseando JSON\n");
        return;
    }
    
    // Obtener tipo de paquete
    cJSON *tipo = cJSON_GetObjectItem(root, "tipo");
    if (!tipo || !cJSON_IsString(tipo)) {
        cJSON_Delete(root);
        return;
    }
    
    const char *tipoStr = tipo->valuestring;
    // Solo log de tipos importantes
    if (strcmp(tipoStr, "BIENVENIDA") == 0 || 
        strcmp(tipoStr, "DESCONEXION") == 0 ||
        strcmp(tipoStr, "ERROR") == 0) {
        printf("[RECV] %s\n", tipoStr);
    }
    
    // Procesar según el tipo
    if (strcmp(tipoStr, "BIENVENIDA") == 0) {
        cJSON *datos = cJSON_GetObjectItem(root, "datos");
        if (datos && cJSON_IsString(datos)) {
            printf("[SERVER] %s\n", datos->valuestring);
            
            // Extraer evento asignado del mensaje
            // "Bienvenido ClienteC al JUEGO_1"
            const char *msg = datos->valuestring;
            if (strstr(msg, "JUEGO_1")) {
                strcpy(g_eventoAsignado, "JUEGO_1");
            } else if (strstr(msg, "JUEGO_2")) {
                strcpy(g_eventoAsignado, "JUEGO_2");
            }
            printf("[INFO] Asignado a: %s\n", g_eventoAsignado);
        }
    }
    else if (strcmp(tipoStr, "MOVIMIENTO") == 0) {
        // Otro jugador se movió
        cJSON *pname = cJSON_GetObjectItem(root, "playerName");
        cJSON *x = cJSON_GetObjectItem(root, "x");
        cJSON *y = cJSON_GetObjectItem(root, "y");
        // cJSON *mov = cJSON_GetObjectItem(root, "movimiento"); // No usado por ahora
        
        if (pname && x && y) {
            printf("[MOVIMIENTO] %s -> (%.1f, %.1f)\n", 
                   pname->valuestring, x->valuedouble, y->valuedouble);
        }
    }
    else if (strcmp(tipoStr, "ESTADO_JUEGO") == 0) {
        // Estado completo del juego
        cJSON *datos = cJSON_GetObjectItem(root, "datos");
        if (datos && cJSON_IsString(datos)) {
            // datos contiene el GameState serializado
            parse_game_state_json(datos->valuestring);
        }
    }
    else if (strcmp(tipoStr, "CREAR_ENEMIGO") == 0) {
        cJSON *enemyTipo = cJSON_GetObjectItem(root, "enemyTipo");
        cJSON *x = cJSON_GetObjectItem(root, "x");
        cJSON *y = cJSON_GetObjectItem(root, "y");
        
        if (enemyTipo && x && y) {
            printf("[CREAR_ENEMIGO] %s en (%.1f, %.1f)\n",
                   enemyTipo->valuestring, x->valuedouble, y->valuedouble);
        }
    }
    else if (strcmp(tipoStr, "DESCONEXION") == 0) {
        cJSON *pname = cJSON_GetObjectItem(root, "playerName");
        if (pname) {
            printf("[DESCONEXION] %s se desconectó\n", pname->valuestring);
        }
    }
    else if (strcmp(tipoStr, "ERROR") == 0) {
        cJSON *datos = cJSON_GetObjectItem(root, "datos");
        if (datos && cJSON_IsString(datos)) {
            printf("[ERROR SERVER] %s\n", datos->valuestring);
        }
    }
    
    cJSON_Delete(root);
}

// -------------------------
// Parse GameState JSON (dentro del campo "datos" del Paquete)
// -------------------------
static void parse_game_state_json(const char *jsonText) {
    if (!jsonText) return;
    
    cJSON *root = cJSON_Parse(jsonText);
    if (!root) {
        printf("[parse_state] Error parseando GameState JSON\n");
        return;
    }
    
    pthread_mutex_lock(&g_state.mutex);
    
    // Evento
    cJSON *evento = cJSON_GetObjectItem(root, "evento");
    if (evento && cJSON_IsString(evento)) {
        strncpy(g_state.evento, evento->valuestring, sizeof(g_state.evento) - 1);
    }
    
    // Timestamp
    cJSON *timestamp = cJSON_GetObjectItem(root, "timestamp");
    if (timestamp && cJSON_IsNumber(timestamp)) {
        g_state.timestamp = (long)timestamp->valuedouble;
    }
    
    // Jugadores (Map<String, Paquete>)
    cJSON *jugadores = cJSON_GetObjectItem(root, "jugadores");
    if (jugadores && cJSON_IsObject(jugadores)) {
        int count = 0;
        cJSON *jugador = NULL;
        
        // Contar jugadores
        cJSON_ArrayForEach(jugador, jugadores) {
            count++;
        }
        
        ensure_player_capacity(&g_state, count);
        g_state.totalJugadores = 0;
        
        // Parsear cada jugador
        cJSON_ArrayForEach(jugador, jugadores) {
            const char *name = jugador->string; // key del map
            
            // Obtener datos del Paquete
            cJSON *x = cJSON_GetObjectItem(jugador, "x");
            cJSON *y = cJSON_GetObjectItem(jugador, "y");
            
            if (x && y) {
                Player *p = &g_state.jugadores[g_state.totalJugadores++];
                strncpy(p->playerName, name, sizeof(p->playerName) - 1);
                p->x = (float)x->valuedouble;
                p->y = (float)y->valuedouble;
                p->vida = 3; // default
            }
        }
        
        printf("[STATE] Jugadores: %d en %s\n", g_state.totalJugadores, g_state.evento);
    }
    
    // TODO: Parsear enemigos y frutas si el servidor los envía
    // (Por ahora el GameState solo tiene jugadores)
    
    pthread_mutex_unlock(&g_state.mutex);
    cJSON_Delete(root);
}

// -------------------------
// Enviar Paquete al servidor (thread-safe)
// -------------------------
static int send_paquete(const char *tipo, const char *movimiento, float x, float y) {
    cJSON *paquete = cJSON_CreateObject();
    
    cJSON_AddStringToObject(paquete, "tipo", tipo);
    cJSON_AddStringToObject(paquete, "playerName", g_playerName);
    
    if (movimiento) {
        cJSON_AddStringToObject(paquete, "movimiento", movimiento);
    }
    
    cJSON_AddNumberToObject(paquete, "x", x);
    cJSON_AddNumberToObject(paquete, "y", y);
    cJSON_AddNumberToObject(paquete, "timestamp", (double)time(NULL) * 1000);
    
    char *jsonStr = cJSON_PrintUnformatted(paquete);
    if (!jsonStr) {
        cJSON_Delete(paquete);
        return -1;
    }
    
    // Solo log de tipos importantes
    if (strcmp(tipo, "CREAR_ENEMIGO") == 0 || strcmp(tipo, "CREAR_FRUTA") == 0) {
        printf("[SEND] %s\n", tipo);
    }
    
    // Enviar con DataOutputStream compatible (UTF modificado de Java)
    // Java usa writeUTF que prefija con 2 bytes de longitud
    size_t len = strlen(jsonStr);
    uint16_t len_network = htons((uint16_t)len);
    
    pthread_mutex_lock(&g_send_mutex);
    
    // Enviar longitud (2 bytes)
    ssize_t w1 = send(g_sock, &len_network, 2, 0);
    // Enviar JSON
    ssize_t w2 = send(g_sock, jsonStr, len, 0);
    
    pthread_mutex_unlock(&g_send_mutex);
    
    free(jsonStr);
    cJSON_Delete(paquete);
    
    if (w1 < 0 || w2 < 0) {
        perror("[send] Error enviando paquete");
        return -1;
    }
    
    return 0;
}

// -------------------------
// Thread de red: recibir paquetes del servidor
// -------------------------
static void *network_thread(void *arg) {
    int sock = *((int*)arg);
    
    while (g_running) {
        // Leer longitud (2 bytes) - writeUTF de Java
        uint16_t len_network;
        ssize_t n = recv(sock, &len_network, 2, MSG_WAITALL);
        
        if (n == 2) {
            uint16_t len = ntohs(len_network);
            
            if (len > 0) {
                char *buffer = malloc(len + 1);
                
                // Leer JSON completo
                ssize_t total = 0;
                while (total < len) {
                    ssize_t r = recv(sock, buffer + total, len - total, 0);
                    if (r <= 0) break;
                    total += r;
                }
                
                if (total == len) {
                    buffer[len] = '\0';
                    
                    // Parsear Paquete JSON
                    parse_paquete_json(buffer);
                }
                
                free(buffer);
            }
        } else if (n == 0) {
            printf("[network] Servidor cerró la conexión\n");
            g_running = false;
            break;
        } else {
            if (errno == EINTR) continue;
            perror("[network] recv");
            g_running = false;
            break;
        }
    }
    
    return NULL;
}

// -------------------------
// Enviar input del teclado
// -------------------------
static void send_input_from_keys(void) {
    static float player_x = SCREEN_WIDTH / 2.0f;
    static float player_y = SCREEN_HEIGHT - 50.0f;
    static float speed = 5.0f;
    
    const char *movimiento = NULL;
    bool moved = false;
    
    if (IsKeyDown(KEY_RIGHT)) {
        player_x += speed;
        movimiento = "DERECHA";
        moved = true;
    }
    if (IsKeyDown(KEY_LEFT)) {
        player_x -= speed;
        movimiento = "IZQUIERDA";
        moved = true;
    }
    if (IsKeyDown(KEY_UP)) {
        player_y -= speed;
        movimiento = "ARRIBA";
        moved = true;
    }
    if (IsKeyDown(KEY_DOWN)) {
        player_y += speed;
        movimiento = "ABAJO";
        moved = true;
    }
    
    // Limitar a pantalla
    if (player_x < 0) player_x = 0;
    if (player_x > SCREEN_WIDTH) player_x = SCREEN_WIDTH;
    if (player_y < 0) player_y = 0;
    if (player_y > SCREEN_HEIGHT) player_y = SCREEN_HEIGHT;
    
    // Solo enviar si hubo movimiento
    if (moved) {
        send_paquete("MOVIMIENTO", movimiento, player_x, player_y);
    }
    
    // Tecla de prueba: crear enemigo
    if (IsKeyPressed(KEY_E)) {
        send_paquete("CREAR_ENEMIGO", NULL, 300, 400);
    }
    
    // Tecla de prueba: crear fruta
    if (IsKeyPressed(KEY_F)) {
        send_paquete("CREAR_FRUTA", NULL, 200, 300);
    }
}

// -------------------------
// Renderizar estado del juego
// -------------------------
static void render_game(Texture2D stageTex) {
    BeginDrawing();
    ClearBackground(RAYWHITE);
    
    // Fondo
    if (stageTex.id != 0) {
        Rectangle src = {0, 0, (float)stageTex.width, (float)stageTex.height};
        Rectangle dst = {0, 0, (float)SCREEN_WIDTH, (float)SCREEN_HEIGHT};
        DrawTexturePro(stageTex, src, dst, (Vector2){0,0}, 0.0f, WHITE);
    }
    
    pthread_mutex_lock(&g_state.mutex);
    
    // Dibujar todos los jugadores
    for (int i = 0; i < g_state.totalJugadores; i++) {
        Player *p = &g_state.jugadores[i];
        
        // Mi jugador en azul, otros en verde
        Color color = (strcmp(p->playerName, g_playerName) == 0) ? BLUE : GREEN;
        
        DrawCircle((int)p->x, (int)p->y, 15, color);
        DrawText(p->playerName, (int)p->x - 20, (int)p->y - 30, 10, BLACK);
    }
    
    // Dibujar enemigos
    for (int i = 0; i < g_state.totalEnemigos; i++) {
        DrawCircle((int)g_state.enemigos[i].x, (int)g_state.enemigos[i].y, 10, RED);
    }
    
    // Dibujar frutas
    for (int i = 0; i < g_state.totalFrutas; i++) {
        if (!g_state.frutas[i].recolectada) {
            DrawCircle((int)g_state.frutas[i].x, (int)g_state.frutas[i].y, 8, YELLOW);
        }
    }
    
    pthread_mutex_unlock(&g_state.mutex);
    
    // UI
    DrawText(TextFormat("Evento: %s", g_eventoAsignado), 10, 10, 20, DARKGREEN);
    DrawText(TextFormat("Jugadores: %d", g_state.totalJugadores), 10, 35, 20, DARKGREEN);
    DrawText("Flechas: Mover | E: Enemigo | F: Fruta", 10, SCREEN_HEIGHT - 25, 15, DARKGRAY);
    
    // Grid
    for (int x = 0; x < SCREEN_WIDTH; x += 32)
        DrawLine(x, 0, x, SCREEN_HEIGHT, Fade(GREEN, 0.15f));
    for (int y = 0; y < SCREEN_HEIGHT; y += 32)
        DrawLine(0, y, SCREEN_WIDTH, y, Fade(GREEN, 0.15f));
    
    EndDrawing();
}

// -------------------------
// Main
// -------------------------
int main(int argc, char *argv[]) {
    // Permitir nombre de jugador como argumento
    if (argc > 1) {
        strncpy(g_playerName, argv[1], sizeof(g_playerName) - 1);
    }
    
    printf("╔════════════════════════════════════════════╗\n");
    printf("║   CLIENTE C - Conectando a Servidor Java  ║\n");
    printf("╚════════════════════════════════════════════╝\n");
    printf("Jugador: %s\n\n", g_playerName);
    
    // Inicializar GameState
    memset(&g_state, 0, sizeof(GameState));
    pthread_mutex_init(&g_state.mutex, NULL);
    
    struct sockaddr_in serv_addr;
    
    // =======================================
    // PASO 1: Crear socket
    // =======================================
    g_sock = socket(AF_INET, SOCK_STREAM, 0);
    if (g_sock < 0) {
        perror("Error al crear socket");
        return -1;
    }
    
    serv_addr.sin_family = AF_INET;
    serv_addr.sin_port = htons(SERVER_PORT);
    
    if (inet_pton(AF_INET, SERVER_IP, &serv_addr.sin_addr) <= 0) {
        perror("Dirección inválida");
        close(g_sock);
        return 1;
    }
    
    // =======================================
    // PASO 2: Conectar
    // =======================================
    printf("Conectando a %s:%d...\n", SERVER_IP, SERVER_PORT);
    
    if (connect(g_sock, (struct sockaddr *)&serv_addr, sizeof(serv_addr)) < 0) {
        perror("Error en connect");
        close(g_sock);
        return 1;
    }
    
    printf("✓ Conectado exitosamente\n\n");
    
    // =======================================
    // PASO 3: Iniciar thread de red
    // =======================================
    pthread_t net_thread;
    if (pthread_create(&net_thread, NULL, network_thread, &g_sock) != 0) {
        perror("pthread_create");
        close(g_sock);
        return 1;
    }
    
    // =======================================
    // PASO 4: Inicializar Raylib
    // =======================================
    InitWindow(SCREEN_WIDTH, SCREEN_HEIGHT, "Cliente C - Java Server");
    SetTargetFPS(TARGET_FPS);
    
    Texture2D playerTex = {0};
    Texture2D stageTex = {0};
    
    if (FileExists(PLAYER_TEXTURE_PATH)) playerTex = LoadTexture(PLAYER_TEXTURE_PATH);
    if (FileExists(STAGE_TEXTURE_PATH)) stageTex = LoadTexture(STAGE_TEXTURE_PATH);
    
    // =======================================
    // PASO 5: Loop principal
    // =======================================
    while (!WindowShouldClose() && g_running) {
        // Procesar input y enviar
        send_input_from_keys();
        
        // Renderizar
        render_game(stageTex);
    }
    
    // =======================================
    // PASO 6: Limpieza
    // =======================================
    printf("\nCerrando conexión...\n");
    
    g_running = false;
    shutdown(g_sock, SHUT_RDWR);
    pthread_join(net_thread, NULL);
    
    if (playerTex.id != 0) UnloadTexture(playerTex);
    if (stageTex.id != 0) UnloadTexture(stageTex);
    CloseWindow();
    
    free(g_state.jugadores);
    free(g_state.enemigos);
    free(g_state.frutas);
    pthread_mutex_destroy(&g_state.mutex);
    
    close(g_sock);
    
    printf("✓ Cliente cerrado correctamente\n");
    return 0;
}