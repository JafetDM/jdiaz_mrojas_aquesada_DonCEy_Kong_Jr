/* SERVER

Pasos:

1 - abrir el socket con funcion socket()
devuelve y prepara un descriptor de fichero 
para la conexion

2 - avisar al sistema operativo con funcion bind()

3 - avisar al sistema que comience a atender la conexion con funcion listen()

4 - pedir y aceptar las conexiones con funcion accept()

5 - escribir y recibir datos con funciones write() y read()

6 - cierre de la comunicacion con funcion close()

*/

/* CLIENTE

Pasos:

1 - abrir el socket con funcion socket()

2 - Solicitar conexion con funcion connect()
En esta llamada se facilita la direccion IP del server y el num de servicio

No servira si servidor no esta escuchando

3 - escribir y recibir datos con funciones write() y read()

6 - cierre de la comunicacion con funcion close()

*/