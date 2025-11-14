#include "raylib.h"
#include "game_state.h"

#define SCREEN_WIDTH 800
#define SCREEN_HEIGHT 600

// =============================
// Función que pinta el juego
// =============================
void render_game(GameState *state, Texture2D player, Texture2D stage) {

    BeginDrawing();
    ClearBackground(BLACK);

    // Dibujar fondo
    Rectangle srcStage = {0, 0, (float)stage.width, (float)stage.height};
    Rectangle dstStage = {0, 0, SCREEN_WIDTH, SCREEN_HEIGHT};
    DrawTexturePro(stage, srcStage, dstStage, (Vector2){0,0}, 0, WHITE);

    // Dibujar jugador
    DrawCircle(state->jugador.x, state->jugador.y, 12, BLUE);

    // Dibujar enemigos
    for (int i = 0; i < state->totalEnemigos; i++) {
        DrawCircle(state->enemigos[i].x, state->enemigos[i].y, 10, RED);
    }

    // Dibujar frutas
    for (int i = 0; i < state->totalFrutas; i++) {
        DrawCircle(state->frutas[i].x, state->frutas[i].y, 8, GREEN);
    }

    // Dibujar grid
    for (int x = 0; x < SCREEN_WIDTH; x += 32)
        DrawLine(x, 0, x, SCREEN_HEIGHT, Fade(GREEN, 0.3f));

    for (int y = 0; y < SCREEN_HEIGHT; y += 32)
        DrawLine(0, y, SCREEN_WIDTH, y, Fade(GREEN, 0.3f));

    EndDrawing();
}


// =============================
// main()
// =============================
int main(void) {

    InitWindow(SCREEN_WIDTH, SCREEN_HEIGHT, "Juego con JSON");
    SetTargetFPS(60);

    Texture2D player = LoadTexture("assets/dk.jpg");
    Texture2D stage  = LoadTexture("assets/stage.png");

    // Estado inicial vacío
    GameState state = {0};

    // DEBUG: posición inicial (será reemplazado por JSON real)
    state.jugador.x = 100;
    state.jugador.y = 500;

    // Bucle principal
    while (!WindowShouldClose()) {

        // Aquí NO se recibe JSON
        // El cliente.c debe actualizar la variable "state"

        render_game(&state, player, stage);
    }

    UnloadTexture(player);
    UnloadTexture(stage);
    CloseWindow();
    return 0;
}