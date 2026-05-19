# Usamos la imagen de Java 21 que ya tenías
FROM eclipse-temurin:21-jdk

# Definimos el directorio de trabajo dentro del contenedor
WORKDIR /app

# Corregido: Copiamos todos los archivos del proyecto al contenedor
# El primer punto es el origen (tu PC) y el segundo el destino (/app)
COPY . .

# Damos permisos de ejecución al wrapper de Maven
RUN chmod +x mvnw

# Compilamos el proyecto saltando los tests para evitar errores de conexión a DB en el build
RUN ./mvnw clean package -Dmaven.test.skip=true

# Renombramos el archivo .jar generado para que sea fácil de ejecutar
# Esto busca cualquier jar en target y lo mueve a la raíz como app.jar
RUN cp target/*.jar app.jar

# Comando para iniciar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]