// cliente.c - Adaptado para comunicarse con servidor Java usando Paquete y GameState
 
// Escalas para renderizado
#define PLAYER_SCALE 0.3f   
#define FRUIT_SCALE  0.2f   
#define ENEMY_TARGET_SIZE_RED   40.0f
#define ENEMY_TARGET_SIZE_BLUE  42.0f 

// Física del juego
#define GRAVITY    900.0f   // píxeles / s^2
#define MOVE_SPEED 220.0f   // píxeles / s
#define JUMP_SPEED -420.0f  // píxeles / s (negativo = hacia arriba)

// Librerías estándar
#include <math.h> 
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <arpa/inet.h>
#include <pthread.h>
#include <stdbool.h>
#include <errno.h>

// Librerías externas
#include "raylib.h"
#include "librerias/cJSON.h"
#include "config.h"
#include "layout.h" 

// ========================
// Estructuras de estado
// ========================

typedef struct {
    char playerName[64];
    float x, y;
    int vida;
    int puntos;
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
    char tipo[32];
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
// Declaraciones adelantadas
// ========================
static void parse_game_state_json(const char *jsonText);
static void parse_paquete_json(const char *jsonText);

static bool g_showLayoutDebug = true;

static GameState g_state;
static int g_sock = -1;
static volatile bool g_running = true;
static pthread_mutex_t g_send_mutex = PTHREAD_MUTEX_INITIALIZER;
static char g_playerName[64] = "ClienteC";
static char g_eventoAsignado[32] = "";

// Física del jugador local (lado cliente)
static float g_playerX = 0.0f;
static float g_playerY = 0.0f;
static float g_playerVy = 0.0f;     // velocidad vertical
static bool  g_playerGrounded = false;
static bool  g_playerInitialized = false;

// Texturas globales
static Texture2D g_playerTex = {0};
static Texture2D g_stageTex  = {0};

// Frutas
static Texture2D g_texMango   = {0};
static Texture2D g_texBanano  = {0};
static Texture2D g_texManzana = {0};

// Cocodrilos rojos
static Texture2D g_texCrocRedUp    = {0};
static Texture2D g_texCrocRedDown  = {0};
static Texture2D g_texCrocRedLeft  = {0};
static Texture2D g_texCrocRedRight = {0};

// Cocodrilos azules
static Texture2D g_texCrocBlueUp    = {0};
static Texture2D g_texCrocBlueDown  = {0};
static Texture2D g_texCrocBlueLeft  = {0};
static Texture2D g_texCrocBlueRight = {0};

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
            
            const char *msg = datos->valuestring;

            // ===== Evento asignado =====
            if (strstr(msg, "JUEGO_1")) {
                strcpy(g_eventoAsignado, "JUEGO_1");
            } else if (strstr(msg, "JUEGO_2")) {
                strcpy(g_eventoAsignado, "JUEGO_2");
            }
            printf("[INFO] Asignado a: %s\n", g_eventoAsignado);

            // ===== Nombre de jugador asignado por el servidor =====
            // Mensaje tipo: "Bienvenido Jugador1 al JUEGO_1"
            const char *inicio = strstr(msg, "Bienvenido ");
            if (inicio) {
                inicio += strlen("Bienvenido ");  // saltar "Bienvenido "
                const char *fin = strstr(inicio, " al ");
                size_t len = fin ? (size_t)(fin - inicio) : strlen(inicio);

                if (len >= sizeof(g_playerName)) {
                    len = sizeof(g_playerName) - 1;
                }

                memcpy(g_playerName, inicio, len);
                g_playerName[len] = '\0';

                printf("[INFO] Nombre jugador asignado por server: %s\n", g_playerName);
            }
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
            cJSON *vida   = cJSON_GetObjectItem(jugador, "vida");
            cJSON *puntos = cJSON_GetObjectItem(jugador, "puntos");
            
            if (x && y) {
                Player *p = &g_state.jugadores[g_state.totalJugadores++];
                memset(p, 0, sizeof(Player));
                
                strncpy(p->playerName, name, sizeof(p->playerName) - 1);
                p->x = (float)x->valuedouble;
                p->y = (float)y->valuedouble;
                
                p->vida   = (vida   && cJSON_IsNumber(vida))   ? vida->valueint   : 3;
                p->puntos = (puntos && cJSON_IsNumber(puntos)) ? puntos->valueint : 0;
            }
        }
        
        printf("[STATE] Jugadores: %d en %s\n", g_state.totalJugadores, g_state.evento);
    }
    
    // TODO: Parsear enemigos y frutas si el servidor los envía
    // (Por ahora el GameState solo tiene jugadores)

    // Enemigos
    cJSON *enemigos = cJSON_GetObjectItem(root, "enemigos");
    if (enemigos && cJSON_IsArray(enemigos)) {
        int count = cJSON_GetArraySize(enemigos);

        ensure_enemy_capacity(&g_state, count);
        g_state.totalEnemigos = 0;

        for (int i = 0; i < count; i++) {
            cJSON *e = cJSON_GetArrayItem(enemigos, i);
            if (!e || !cJSON_IsObject(e)) continue;

            cJSON *id     = cJSON_GetObjectItem(e, "id");
            cJSON *tipo   = cJSON_GetObjectItem(e, "tipo");
            cJSON *x      = cJSON_GetObjectItem(e, "x");
            cJSON *y      = cJSON_GetObjectItem(e, "y");
            cJSON *vel    = cJSON_GetObjectItem(e, "velocidad");
            cJSON *dir    = cJSON_GetObjectItem(e, "direccion");  

            Enemy *dst = &g_state.enemigos[g_state.totalEnemigos++];
            memset(dst, 0, sizeof(Enemy));

            if (id && cJSON_IsNumber(id)) {
                snprintf(dst->id, sizeof(dst->id), "%d", (int)id->valuedouble);
            } else {
                strcpy(dst->id, "");
            }

            if (tipo && cJSON_IsString(tipo)) {
                strncpy(dst->tipo, tipo->valuestring, sizeof(dst->tipo) - 1);
            }

            dst->x = x ? (float)x->valuedouble : 0.0f;
            dst->y = y ? (float)y->valuedouble : 0.0f;
            dst->velocidad = vel ? (float)vel->valuedouble : 0.0f;

            if (dir && cJSON_IsString(dir)) {               
                strncpy(dst->direccion, dir->valuestring,
                        sizeof(dst->direccion) - 1);
            } else {
                strcpy(dst->direccion, "");
            }
        }
    }

    // Frutas
    cJSON *frutas = cJSON_GetObjectItem(root, "frutas");
    if (frutas && cJSON_IsArray(frutas)) {
        int count = cJSON_GetArraySize(frutas);

        ensure_fruit_capacity(&g_state, count);
        g_state.totalFrutas = 0;

        for (int i = 0; i < count; i++) {
            cJSON *f = cJSON_GetArrayItem(frutas, i);
            if (!f || !cJSON_IsObject(f)) continue;

            cJSON *id        = cJSON_GetObjectItem(f, "id");
            cJSON *x         = cJSON_GetObjectItem(f, "x");
            cJSON *y         = cJSON_GetObjectItem(f, "y");
            cJSON *puntos    = cJSON_GetObjectItem(f, "puntos");
            cJSON *recolecta = cJSON_GetObjectItem(f, "recolectada");
            cJSON *tipo      = cJSON_GetObjectItem(f, "tipo");  

            Fruit *dst = &g_state.frutas[g_state.totalFrutas++];
            memset(dst, 0, sizeof(Fruit));

            if (id && cJSON_IsNumber(id)) {
                snprintf(dst->id, sizeof(dst->id), "%d", (int)id->valuedouble);
            } else {
                strcpy(dst->id, "");
            }

            dst->x = x ? (float)x->valuedouble : 0.0f;
            dst->y = y ? (float)y->valuedouble : 0.0f;
            dst->puntos = puntos ? puntos->valueint : 0;

            if (recolecta && cJSON_IsBool(recolecta)) {
                dst->recolectada = cJSON_IsTrue(recolecta);
            } else {
                dst->recolectada = false;
            }

            if (tipo && cJSON_IsString(tipo)) {              
                strncpy(dst->tipo, tipo->valuestring,
                        sizeof(dst->tipo) - 1);
            } else {
                strcpy(dst->tipo, "");
            }
        }
    }
    
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

    // Campos especiales según el tipo de paquete
    if (strcmp(tipo, "CREAR_ENEMIGO") == 0) {
        // Por ahora siempre creamos cocodrilo rojo
        cJSON_AddStringToObject(paquete, "enemyTipo", "CROC_RED");
    } else if (strcmp(tipo, "CREAR_FRUTA") == 0) {
        // Puntos fijos de ejemplo
        cJSON_AddNumberToObject(paquete, "puntos", 50);
    }
    
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

// Calcula tamaño "de pies" del DK Jr basado en la textura
static float get_player_half_height(void) {
    if (g_playerTex.id != 0) {
        float h = (float)g_playerTex.height * PLAYER_SCALE;
        return h * 0.5f;
    }
    return 24.0f; // valor por defecto
}

// Colocar al jugador sobre la plataforma 0 al inicio
static void init_player_start_position(void) {
    if (g_playerInitialized) return;

    const PlataformaDef *floor = &PLATAFORMAS[0];

    float halfH = get_player_half_height();

    g_playerX = (floor->xLeft + floor->xRight) * 0.5f;  // centro de la plataforma
    g_playerY = floor->y - halfH;                       // pies justo sobre la plataforma
    g_playerVy = 0.0f;
    g_playerGrounded = true;
    g_playerInitialized = true;

    // Mandar posición inicial al servidor
    send_paquete("MOVIMIENTO", "QUIETO", g_playerX, g_playerY);
}

// Resolver colisión con todas las plataformas
static void resolver_colision_plataformas(void) {
    if (!g_playerInitialized) return;

    float halfH = get_player_half_height();
    float feetY = g_playerY + halfH;

    g_playerGrounded = false;
    const float tolerancia = 6.0f;  // rango para "aterrizar" en la plataforma

    for (int i = 0; i < NUM_PLATAFORMAS; i++) {
        const PlataformaDef *p = &PLATAFORMAS[i];

        // ¿Estamos horizontalmente sobre la plataforma?
        if (g_playerX < p->xLeft || g_playerX > p->xRight) {
            continue;
        }

        float platY = p->y;

        // Solo colisionamos si venimos cayendo (vy >= 0)
        if (g_playerVy >= 0.0f &&
            feetY >= platY - tolerancia &&
            feetY <= platY + tolerancia) {

            // Ajustar al jugador para que quede "parado" justo sobre la plataforma
            g_playerY = platY - halfH;
            g_playerVy = 0.0f;
            g_playerGrounded = true;
            break;
        }
    }
}

// -------------------------
// Enviar input del teclado + física del jugador
// -------------------------
static void send_input_from_keys(void) {
    // Asegurar que el jugador tenga posición inicial sobre la plataforma 0
    init_player_start_position();

    float dt = GetFrameTime();
    if (dt <= 0.0f) dt = 1.0f / 60.0f;

    float dx = 0.0f;
    const char *movimiento = "QUIETO";

    // Movimiento lateral
    if (IsKeyDown(KEY_RIGHT)) {
        dx += MOVE_SPEED * dt;
        movimiento = "DERECHA";
    }
    if (IsKeyDown(KEY_LEFT)) {
        dx -= MOVE_SPEED * dt;
        movimiento = "IZQUIERDA";
    }

    // Salto (espacio) solo si está en el suelo
    if (IsKeyPressed(KEY_SPACE) && g_playerGrounded) {
        g_playerVy = JUMP_SPEED;
        g_playerGrounded = false;
        movimiento = "ARRIBA";
    }

    // Física vertical: gravedad
    g_playerVy += GRAVITY * dt;
    g_playerY  += g_playerVy * dt;

    // Movimiento horizontal
    g_playerX += dx;

    // Limitar a la pantalla
    if (g_playerX < 0) g_playerX = 0;
    if (g_playerX > SCREEN_WIDTH) g_playerX = SCREEN_WIDTH;

    // Colisión con plataformas
    resolver_colision_plataformas();

    // Enviar al servidor solo si la posición cambió
    static float lastX = 0.0f;
    static float lastY = 0.0f;
    static bool firstSend = true;

    if (firstSend ||
        fabsf(g_playerX - lastX) > 0.1f ||
        fabsf(g_playerY - lastY) > 0.1f) {

        send_paquete("MOVIMIENTO", movimiento, g_playerX, g_playerY);
        lastX = g_playerX;
        lastY = g_playerY;
        firstSend = false;
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
        
        if (strcmp(p->playerName, g_playerName) == 0 && g_playerTex.id != 0) {
            Rectangle src = { 0, 0, (float)g_playerTex.width, (float)g_playerTex.height };

            float w = g_playerTex.width  * PLAYER_SCALE;
            float h = g_playerTex.height * PLAYER_SCALE;

            Rectangle dst = { p->x, p->y, w, h };
            Vector2 origin = { w / 2.0f, h / 2.0f };   // ojo: mitad del tamaño escalado

            DrawTexturePro(g_playerTex, src, dst, origin, 0.0f, WHITE);
        } else {
            // Otros jugadores como círculo por ahora
            Color color = GREEN;
            DrawCircle((int)p->x, (int)p->y, 15, color);
        }

        DrawText(p->playerName, (int)p->x - 20, (int)p->y - 30, 10, BLACK);
    }
    
    // Dibujar enemigos
    for (int i = 0; i < g_state.totalEnemigos; i++) {
        Enemy *e = &g_state.enemigos[i];

        Texture2D tex = {0};

        bool isRed  = (strncmp(e->tipo, "CROC_RED", 8)  == 0);
        bool isBlue = (strncmp(e->tipo, "CROC_BLUE", 9) == 0);
        const char *dir = e->direccion;

        if (isRed) {
            if (strcmp(dir, "UP") == 0 && g_texCrocRedUp.id)
                tex = g_texCrocRedUp;
            else if (strcmp(dir, "DOWN") == 0 && g_texCrocRedDown.id)
                tex = g_texCrocRedDown;
            else if (strcmp(dir, "LEFT") == 0 && g_texCrocRedLeft.id)
                tex = g_texCrocRedLeft;
            else if (strcmp(dir, "RIGHT") == 0 && g_texCrocRedRight.id)
                tex = g_texCrocRedRight;
        } else if (isBlue) {
            if (strcmp(dir, "UP") == 0 && g_texCrocBlueUp.id)
                tex = g_texCrocBlueUp;
            else if (strcmp(dir, "DOWN") == 0 && g_texCrocBlueDown.id)
                tex = g_texCrocBlueDown;
            else if (strcmp(dir, "LEFT") == 0 && g_texCrocBlueLeft.id)
                tex = g_texCrocBlueLeft;
            else if (strcmp(dir, "RIGHT") == 0 && g_texCrocBlueRight.id)
                tex = g_texCrocBlueRight;
        }

        if (tex.id != 0) {
            Rectangle src = { 0, 0, (float)tex.width, (float)tex.height };

            float texW = (float)tex.width;
            float texH = (float)tex.height;

            // Usamos la dimensión mayor para mantener proporción sin deformar
            float mayor = (texW > texH) ? texW : texH;

            // Tamaño objetivo distinto según si es rojo o azul
            float targetSize = ENEMY_TARGET_SIZE_RED; // por defecto rojo
            if (isBlue) {
                targetSize = ENEMY_TARGET_SIZE_BLUE;
            }

            // Escala para que "mayor" pase a ser targetSize
            float scale = targetSize / mayor;

            float w = texW * scale;
            float h = texH * scale;

            Rectangle dst = (Rectangle){ e->x, e->y, w, h };
            Vector2 origin = (Vector2){ w / 2.0f, h / 2.0f };

            DrawTexturePro(tex, src, dst, origin, 0.0f, WHITE);
        } else {
            // Fallback si no hay textura
            DrawCircle((int)e->x, (int)e->y, 10, RED);
        }
    }
    
    // Dibujar frutas
    for (int i = 0; i < g_state.totalFrutas; i++) {
        Fruit *f = &g_state.frutas[i];

        if (f->recolectada) continue;

        Texture2D tex = {0};

        if (strcmp(f->tipo, "MANGO") == 0 && g_texMango.id) {
            tex = g_texMango;
        } else if (strcmp(f->tipo, "BANANO") == 0 && g_texBanano.id) {
            tex = g_texBanano;
        } else if (strcmp(f->tipo, "MANZANA") == 0 && g_texManzana.id) {
            tex = g_texManzana;
        }

        if (tex.id != 0) {
            Rectangle src = { 0, 0, (float)tex.width, (float)tex.height };

            float w = tex.width  * FRUIT_SCALE;
            float h = tex.height * FRUIT_SCALE;

            Rectangle dst = { f->x, f->y, w, h };
            Vector2 origin = { w / 2.0f, h / 2.0f };

            DrawTexturePro(tex, src, dst, origin, 0.0f, WHITE);
        } else {
            // Fallback si algo falla
            DrawCircle((int)f->x, (int)f->y, 8, YELLOW);
        }
    }
    
    // Dibujar layout debug si está activado
    if (g_showLayoutDebug) {
        // Dibujar PLATAFORMAS como segmentos horizontales
        for (int i = 0; i < NUM_PLATAFORMAS; i++) {
            const PlataformaDef *p = &PLATAFORMAS[i];

            DrawLine((int)p->xLeft, (int)p->y, (int)p->xRight, (int)p->y,
                     Fade(RED, 0.7f));

            // Etiqueta en el centro
            float midX = (p->xLeft + p->xRight) * 0.5f;
            DrawText(TextFormat("P%d", i), (int)midX - 10, (int)p->y - 15, 14, RED);
        }

        // Dibujar LIANAS como segmentos verticales
        for (int j = 0; j < NUM_LIANAS; j++) {
            const LianaDef *l = &LIANAS[j];

            DrawLine((int)l->x, (int)l->yTop, (int)l->x, (int)l->yBottom,
                     Fade(BLUE, 0.7f));

            // Etiqueta en la parte de arriba
            DrawText(TextFormat("L%d", j), (int)l->x - 10, (int)l->yTop - 20, 14, BLUE);
        }
    }

    // Obtener vidas y puntos de mi jugador
    int vidaLocal = -1;
    int puntosLocal = 0;

    for (int i = 0; i < g_state.totalJugadores; i++) {
        Player *p = &g_state.jugadores[i];
        if (strcmp(p->playerName, g_playerName) == 0) {
            vidaLocal = p->vida;
            puntosLocal = p->puntos;
            break;
        }
    }
    
    pthread_mutex_unlock(&g_state.mutex);
    
    // UI
    DrawText(TextFormat("Evento: %s", g_eventoAsignado), 10, 10, 20, DARKGREEN);
    DrawText(TextFormat("Jugadores: %d", g_state.totalJugadores), 10, 35, 20, DARKGREEN);
    DrawText("Flechas: Mover | E: Enemigo | F: Fruta", 10, SCREEN_HEIGHT - 25, 15, DARKGRAY);
    DrawText(TextFormat("Vidas: %d", (vidaLocal >= 0 ? vidaLocal : 0)),
             10, 60, 20, RED);
    DrawText(TextFormat("Puntos: %d", puntosLocal),
             10, 85, 20, GOLD);
             
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
    
    // Texturas locales
    Texture2D playerTex = {0};
    Texture2D stageTex  = {0};

    if (FileExists(PLAYER_TEXTURE_PATH)) {
        playerTex = LoadTexture(PLAYER_TEXTURE_PATH);
    }
    if (FileExists(STAGE_TEXTURE_PATH)) {
        stageTex = LoadTexture(STAGE_TEXTURE_PATH);
    }

    // Copiarlas a las globales para usarlas en render_game
    g_playerTex = playerTex;
    g_stageTex  = stageTex;

    // Cargar frutas (ajusta las rutas a tus archivos reales)
    if (FileExists("assets/mango.png"))   g_texMango   = LoadTexture("assets/mango.png");
    if (FileExists("assets/banano.png"))  g_texBanano  = LoadTexture("assets/banano.png");
    if (FileExists("assets/manzana.png")) g_texManzana = LoadTexture("assets/manzana.png");

    // Cocodrilos rojos
    if (FileExists("assets/r_arr.png"))    g_texCrocRedUp    = LoadTexture("assets/r_arr.png");
    if (FileExists("assets/r_ab.png"))  g_texCrocRedDown  = LoadTexture("assets/r_ab.png");
    if (FileExists("assets/r_izq.png"))  g_texCrocRedLeft  = LoadTexture("assets/r_izq.png");
    if (FileExists("assets/r_der.png")) g_texCrocRedRight = LoadTexture("assets/r_der.png");

    // Cocodrilos azules
    if (FileExists("assets/a_arr.png"))    g_texCrocBlueUp    = LoadTexture("assets/a_arr.png");
    if (FileExists("assets/a_ab.png"))  g_texCrocBlueDown  = LoadTexture("assets/a_ab.png");
    if (FileExists("assets/a_izq.png"))  g_texCrocBlueLeft  = LoadTexture("assets/a_izq.png");
    if (FileExists("assets/a_der.png")) g_texCrocBlueRight = LoadTexture("assets/a_der.png");
    
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
    if (g_texMango.id)        UnloadTexture(g_texMango);
    if (g_texBanano.id)       UnloadTexture(g_texBanano);
    if (g_texManzana.id)      UnloadTexture(g_texManzana);

    if (g_texCrocRedUp.id)    UnloadTexture(g_texCrocRedUp);
    if (g_texCrocRedDown.id)  UnloadTexture(g_texCrocRedDown);
    if (g_texCrocRedLeft.id)  UnloadTexture(g_texCrocRedLeft);
    if (g_texCrocRedRight.id) UnloadTexture(g_texCrocRedRight);

    if (g_texCrocBlueUp.id)    UnloadTexture(g_texCrocBlueUp);
    if (g_texCrocBlueDown.id)  UnloadTexture(g_texCrocBlueDown);
    if (g_texCrocBlueLeft.id)  UnloadTexture(g_texCrocBlueLeft);
    if (g_texCrocBlueRight.id) UnloadTexture(g_texCrocBlueRight);

    CloseWindow();
    
    free(g_state.jugadores);
    free(g_state.enemigos);
    free(g_state.frutas);
    pthread_mutex_destroy(&g_state.mutex);
    
    close(g_sock);
    
    printf("✓ Cliente cerrado correctamente\n");
    return 0;
}