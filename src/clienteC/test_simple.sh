#!/bin/bash

echo "╔════════════════════════════════════════╗"
echo "║  TEST SIMPLE - ¿Funciona el sistema?  ║"
echo "╚════════════════════════════════════════╝"
echo ""

# Verificar servidor
echo "1. Verificando servidor..."
if nc -z localhost 8080 2>/dev/null; then
    echo "   ✓ Servidor corriendo en puerto 8080"
else
    echo "   ✗ Servidor NO está corriendo"
    echo ""
    echo "Inicia el servidor con:"
    echo "  mvn exec:java -Dexec.mainClass=\"serverJava.GameServer\""
    exit 1
fi

echo ""
echo "2. Prueba el cliente:"
echo "   - Abre la ventana gráfica"
echo "   - Mueve con las flechas"
echo "   - Deberías ver un círculo azul moviéndose"
echo "   - Presiona E para crear enemigo (círculo rojo)"
echo "   - Presiona F para crear fruta (círculo amarillo)"
echo ""
echo "3. Prueba con 2 clientes:"
echo "   Terminal 1: ./cliente Jugador1"
echo "   Terminal 2: ./cliente Jugador2"
echo "   - Ambos van a JUEGO_1 y JUEGO_2 respectivamente"
echo "   - NO se verán entre sí (están en juegos diferentes)"
echo ""
echo "4. Para que se vean entre sí:"
echo "   Terminal 1: ./cliente J1"
echo "   Terminal 2: (espera 2 segundos)"
echo "   Terminal 2: ./cliente J2"
echo "   Terminal 3: (espera 2 segundos)"  
echo "   Terminal 3: ./cliente J3"
echo "   - J1 y J3 estarán en JUEGO_1 (impares)"
echo "   - J2 estará en JUEGO_2 (par)"
echo "   - J1 y J3 se verán entre sí"
echo ""
echo "════════════════════════════════════════"
echo ""
read -p "Presiona Enter para iniciar el cliente..."

./cliente