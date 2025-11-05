#include "raylib.h"

#define TILE_SIZE 32
#define MAP_WIDTH 10
#define MAP_HEIGHT 8


int main(void) {
    const int screenWidth = 800;
    const int screenHeight = 600;

    InitWindow(screenWidth, screenHeight, "Donkey Kong Jr Stage");
    SetTargetFPS(60);

    // Cargar textura (sprite)
    Texture2D player = LoadTexture("assets/dk.jpg");

    // Carga la textura del stage (el fondo)
    Texture2D stage = LoadTexture("assets/stage.png");

    // ======================================
    // Rectangulos y vector para el stage fondo
    // ======================================
    // Rectangulo fuente (para dibujarlo)
    Rectangle source = {0, 0, (float)stage.width, (float)stage.height};
    // Rectangulo destino (tamaño en pantalla)
    Rectangle dest = {0, 0, (float)screenWidth, (float)screenHeight}; 
    // Esta en 0,0
    Vector2 origin = {0,0};

    // ======================================
    // Rectangulos y vector para DK jr
    // ======================================

    // Rectangulo fuente (para dibujarlo)
    Rectangle source_dk = {0, 0, (float)player.width, (float)player.height};
    // tamaño deseado
    float playerSize = 50.0f;
    // Posición inicial
    Vector2 playerPos = { 100, 500 };

    while (!WindowShouldClose()) {
        // Movimiento simple (opcional)
        if (IsKeyDown(KEY_RIGHT)) playerPos.x += 2;
        if (IsKeyDown(KEY_LEFT))  playerPos.x -= 2;

        BeginDrawing();
            ClearBackground(BLACK);

            // Dibuja el fondo
            DrawTexturePro(stage, source, dest, origin, 0.0f, WHITE);

            // Crear el rectángulo destino dinámico (en cada bucle) según la posición
            Rectangle dest_dk = { playerPos.x, playerPos.y, playerSize, playerSize };

            // Dibujar sprite en posición específica
            DrawTexturePro(player, source_dk, dest_dk, (Vector2){0,0}, 0.0f, WHITE);
            
            // Dibujar cuadricula
            int tileSize = 32;
            int cols = screenWidth / tileSize;
            int rows = screenHeight / tileSize;

            for (int i = 0; i <= cols; i++) {
                DrawLine(i * tileSize, 0, i * tileSize, screenHeight, Fade(GREEN, 0.3f));
            }
            for (int j = 0; j <= rows; j++) {
                DrawLine(0, j * tileSize, screenWidth, j * tileSize, Fade(GREEN, 0.3f));
            }


        EndDrawing();
    }

    // Liberar textura
    UnloadTexture(player);
    UnloadTexture(stage);

    CloseWindow();

    return 0;
}