#!/bin/bash
set -e

echo "=========================================="
echo "  Instalando Raylib para Ubuntu"
echo "=========================================="

# Instalar dependencias
echo "📦 Instalando dependencias..."
sudo apt install -y build-essential git cmake libasound2-dev mesa-common-dev \
    libx11-dev libxrandr-dev libxi-dev xorg-dev libgl1-mesa-dev libglu1-mesa-dev gcc

# Clonar Raylib
echo "📥 Descargando Raylib..."
cd /tmp
rm -rf raylib
git clone https://github.com/raysan5/raylib.git
cd raylib

# Compilar
echo "🔨 Compilando Raylib..."
mkdir -p build && cd build
cmake .. -DBUILD_SHARED_LIBS=ON
make -j$(nproc)

# Instalar
echo "📦 Instalando Raylib..."
sudo make install
sudo ldconfig

# Verificar
echo "✅ Verificando instalación..."
if [ -f /usr/local/lib/libraylib.so ] && [ -f /usr/local/include/raylib.h ]; then
    echo "✓ Raylib instalado correctamente en:"
    echo "  - Librería: /usr/local/lib/libraylib.so"
    echo "  - Headers: /usr/local/include/raylib.h"
else
    echo "❌ Error: Raylib no se instaló correctamente"
    exit 1
fi

echo ""
echo "=========================================="
echo "  ✓ Instalación completada"
echo "=========================================="
echo ""
echo "Ahora puedes compilar tu proyecto:"
echo "  cd ~/Documentos/Proyectos/DonkCE_Kong_Jr/jdiaz_mrojas_aquesada_DonCEy_Kong_Jr/src/clienteC"
echo "  make clean"
echo "  make"