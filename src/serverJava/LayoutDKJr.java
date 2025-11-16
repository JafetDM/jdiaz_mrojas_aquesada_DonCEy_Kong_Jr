package serverJava;

import java.util.List;

/**
 * LayoutDKJr
 *
 * Define la geometría del nivel (lianas y plataformas) para el servidor.
 * Estos valores deben coincidir con los del cliente (layout.h).
 */
public class LayoutDKJr {

    // ----- Lianas: misma X, pero con Y de inicio y fin -----
    public static class LianaDef {
        public final float x;
        public final float yTop;
        public final float yBottom;

        public LianaDef(float x, float yTop, float yBottom) {
            this.x = x;
            this.yTop = yTop;
            this.yBottom = yBottom;
        }

        @Override
        public String toString() {
            return "LianaDef{x=" + x + ", yTop=" + yTop + ", yBottom=" + yBottom + "}";
        }
    }

    // ----- Plataformas: misma Y, pero con X de inicio y fin -----
    public static class PlataformaDef {
        public final float xLeft;
        public final float xRight;
        public final float y;

        public PlataformaDef(float xLeft, float xRight, float y) {
            this.xLeft = xLeft;
            this.xRight = xRight;
            this.y = y;
        }

        @Override
        public String toString() {
            return "PlataformaDef{xLeft=" + xLeft + ", xRight=" + xRight + ", y=" + y + "}";
        }
    }

    // ===== Deben coincidir con layout.h del cliente =====

    public static final int NUM_LIANAS = 14;
    public static final int NUM_PLATAFORMAS = 11;

    public static final LianaDef[] LIANAS = {
        new LianaDef( 22.0f, 192.0f, 522.0f ),  // L0
        new LianaDef(106.0f, 192.0f, 502.0f ),  // L1
        new LianaDef(443.0f, 192.0f, 355.0f ),  // L2
        new LianaDef(330.0f, 192.0f, 468.0f ),  // L3
        new LianaDef(189.0f, 301.0f, 393.0f ),  // L4
        new LianaDef(189.0f, 413.0f, 522.0f ),  // L5
        new LianaDef(526.0f, 211.0f, 468.0f ),  // L6
        new LianaDef(611.0f, 210.0f, 428.0f ),  // L7
        new LianaDef(695.0f, 118.0f, 337.0f ),  // L8
        new LianaDef(694.0f, 357.0f, 467.0f ),  // L9
        new LianaDef(779.0f, 117.0f, 337.0f ),  // L10
        new LianaDef(779.0f, 357.0f, 467.0f ),  // L11
        new LianaDef(498.0f,  62.0f, 115.0f ),  // L12
        new LianaDef(330.0f,  62.0f, 117.0f )   // L13
    };

    public static final PlataformaDef[] PLATAFORMAS = {
        new PlataformaDef(  6.0f, 198.0f, 560.0f ),  // P0: piso
        new PlataformaDef(118.0f, 286.0f, 393.0f ),  // P1
        new PlataformaDef(118.0f, 234.0f, 285.0f ),  // P2
        new PlataformaDef(  6.0f, 482.0f, 172.0f ),  // P3
        new PlataformaDef(456.0f, 678.0f, 190.0f ),  // P4
        new PlataformaDef(626.0f, 791.0f, 337.0f ),  // P5
        new PlataformaDef(290.0f, 394.0f, 522.0f ),  // P6
        new PlataformaDef(430.0f, 509.0f, 539.0f ),  // P7
        new PlataformaDef(540.0f, 652.0f, 521.0f ),  // P8
        new PlataformaDef(682.0f, 786.0f, 504.0f ),  // P9
        new PlataformaDef(203.0f, 288.0f,  99.0f )   // P10
    };

    // ===== Helpers básicos =====

    public static LianaDef getLiana(int index) {
        if (index < 0 || index >= LIANAS.length) {
            throw new IllegalArgumentException("Liana inválida: " + index);
        }
        return LIANAS[index];
    }

    public static PlataformaDef getPlataforma(int index) {
        if (index < 0 || index >= PLATAFORMAS.length) {
            throw new IllegalArgumentException("Plataforma inválida: " + index);
        }
        return PLATAFORMAS[index];
    }

    /** X de la liana (compatibilidad con versión anterior). */
    public static float getXForLiana(int lianaIndex) {
        return getLiana(lianaIndex).x;
    }

    /** Y de la plataforma (compatibilidad con versión anterior). */
    public static float getYForPlataforma(int plataformaIndex) {
        return getPlataforma(plataformaIndex).y;
    }

    /**
     * Y inicial para un cocodrilo azul que baja por la liana:
     * un poquito por encima del tope de esa liana.
     */
    public static float getYTopLiana(int lianaIndex) {
        LianaDef l = getLiana(lianaIndex);
        return l.yTop - 20.0f;   // ajusta el offset si querés
    }

    /**
     * X del centro de una plataforma (para poner cocodrilos rojos en plataforma).
     */
    public static float getXCentroPlataforma(int plataformaIndex) {
        PlataformaDef p = getPlataforma(plataformaIndex);
        return (p.xLeft + p.xRight) * 0.5f;
    }

    /**
     * Y sobre una liana, según "altura" discreta:
     * 0 = arriba, 1 = medio, 2 = abajo (para frutas).
     */
    public static float getYOnLiana(int lianaIndex, int alturaIndex) {
        LianaDef l = getLiana(lianaIndex);
        switch (alturaIndex) {
            case 0: // arriba
                return l.yTop + 10.0f;
            case 1: // medio
                return (l.yTop + l.yBottom) * 0.5f;
            case 2: // abajo
                return l.yBottom - 10.0f;
            default:
                throw new IllegalArgumentException("Altura inválida (usa 0,1,2): " + alturaIndex);
        }
    }

    // ===== Helpers "de lista" (por si los querés) =====

    public static List<LianaDef> getAllLianas() {
        return List.of(LIANAS);
    }

    public static List<PlataformaDef> getAllPlataformas() {
        return List.of(PLATAFORMAS);
    }

    public static int getCantidadLianas() {
        return LIANAS.length;
    }

    public static int getCantidadPlataformas() {
        return PLATAFORMAS.length;
    }
}
