// layout.h

#ifndef LAYOUT_H
#define LAYOUT_H

// ----- Lianas: misma X, pero con Y de inicio y fin -----
typedef struct {
    float x;
    float yTop;
    float yBottom;
} LianaDef;

// ----- Plataformas: misma Y, pero con X de inicio y fin -----
typedef struct {
    float xLeft;
    float xRight;
    float y;
} PlataformaDef;

// Ajusta estos números a tu escenario real
#define NUM_LIANAS 14
#define NUM_PLATAFORMAS 11

// Ejemplo de datos *inventados* para empezar
static const LianaDef LIANAS[NUM_LIANAS] = {
    {  22.0f, 192.0f, 522.0f },  // L0
    { 106.0f, 192.0f, 502.0f },  // L1
    { 443.0f, 192.0f, 355.0f },  // L2
    { 330.0f, 192.0f, 468.0f },  // L3
    { 189.0f, 301.0f, 393.0f },
    { 189.0f, 413.0f, 522.0f },
    { 526.0f, 211.0f, 468.0f },
    { 611.0f, 210.0f, 428.0f },
    { 695.0f, 118.0f, 337.0f },
    { 694.0f, 357.0f, 467.0f },
    { 779.0f, 117.0f, 337.0f },
    { 779.0f, 357.0f, 467.0f },
    { 498.0f, 62.0f, 115.0f },
    { 330.0f, 62.0f, 117.0f }
};

static const PlataformaDef PLATAFORMAS[NUM_PLATAFORMAS] = {
    {  6.0f, 198.0f, 560.0f },  // P0: piso
    { 118.0f, 286.0f, 393.0f },  // P1: ejemplo
    { 118.0f, 234.0f, 285.0f },  // P2
    { 6.0f, 482.0f, 172.0f },
    { 456.0f, 678.0f, 190.0f },
    { 626.0f, 791.0f, 337.0f },
    { 290.0f, 394.0f, 522.0f },
    { 430.0f, 509.0f, 539.0f },
    { 540.0f, 652.0f, 521.0f },
    { 682.0f, 786.0f, 504.0f },
    { 203.0f, 288.0f, 99.0f }
};

#endif