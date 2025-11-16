// coord_editor.c - Mini editor de lianas/plataformas
#include "raylib.h"
#include "config.h"
#include <stdio.h>

typedef enum {
    MODE_LIANA,
    MODE_PLATAFORMA
} EditMode;

int main(void) {

    InitWindow(SCREEN_WIDTH, SCREEN_HEIGHT, "Editor de coordenadas DK Jr");
    SetTargetFPS(60);

    Texture2D stageTex = {0};
    if (FileExists(STAGE_TEXTURE_PATH)) {
        stageTex = LoadTexture(STAGE_TEXTURE_PATH);
    }

    EditMode mode = MODE_LIANA;
    int clickPhase = 0;      // 0 = esperando primer clic, 1 = segundo
    Vector2 firstClick = {0};

    while (!WindowShouldClose()) {

        Vector2 mouse = GetMousePosition();

        // Cambiar modo con teclado
        if (IsKeyPressed(KEY_L)) {
            mode = MODE_LIANA;
            clickPhase = 0;
        }
        if (IsKeyPressed(KEY_P)) {
            mode = MODE_PLATAFORMA;
            clickPhase = 0;
        }

        // Manejo de clics
        if (IsMouseButtonPressed(MOUSE_LEFT_BUTTON)) {
            if (clickPhase == 0) {
                // Primer punto
                firstClick = mouse;
                clickPhase = 1;
            } else {
                // Segundo punto
                if (mode == MODE_LIANA) {
                    float x = firstClick.x;
                    float yTop    = (firstClick.y < mouse.y) ? firstClick.y : mouse.y;
                    float yBottom = (firstClick.y > mouse.y) ? firstClick.y : mouse.y;

                    // Ajusta el formato a tu struct de liana
                    printf("Liana:      { %.1ff, %.1ff, %.1ff },\n", x, yTop, yBottom);
                } else { // MODE_PLATAFORMA
                    float x1 = firstClick.x;
                    float x2 = mouse.x;
                    float y  = firstClick.y;   // usamos la y del primer clic

                    if (x2 < x1) {
                        float tmp = x1; x1 = x2; x2 = tmp;
                    }

                    // Ajusta el formato a tu struct de plataforma
                    printf("Plataforma: { %.1ff, %.1ff, %.1ff },\n", x1, x2, y);
                }

                fflush(stdout); // para que salga al toque en la terminal
                clickPhase = 0;
            }
        }

        // ====== Dibujo ======
        BeginDrawing();
        ClearBackground(BLACK);

        if (stageTex.id != 0) {
            Rectangle src = {0, 0, (float)stageTex.width, (float)stageTex.height};
            Rectangle dst = {0, 0, (float)SCREEN_WIDTH, (float)SCREEN_HEIGHT};
            DrawTexturePro(stageTex, src, dst, (Vector2){0,0}, 0, WHITE);
        }

        // Mira del mouse
        DrawLine((int)mouse.x - 5, (int)mouse.y,     (int)mouse.x + 5, (int)mouse.y, GREEN);
        DrawLine((int)mouse.x,     (int)mouse.y - 5, (int)mouse.x,     (int)mouse.y + 5, GREEN);

        // Mostrar coords
        DrawText(TextFormat("x=%.1f  y=%.1f", mouse.x, mouse.y), 10, 10, 20, YELLOW);

        // Mostrar modo actual
        DrawText(
            (mode == MODE_LIANA)
                ? "Modo: LIANA (L=liana, P=plataforma)"
                : "Modo: PLATAFORMA (L=liana, P=plataforma)",
            10, 35, 18, LIGHTGRAY
        );

        // Marcar primer clic si ya se dio
        if (clickPhase == 1) {
            DrawCircleV(firstClick, 4, RED);
        }

        EndDrawing();
    }

    if (stageTex.id != 0) UnloadTexture(stageTex);
    CloseWindow();
    return 0;
}
