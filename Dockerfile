# Usamos la imagen de Java 21 que ya tenías
FROM eclipse-temurin:21-jdk

# Forzamos al contenedor a usar UTF-8 para evitar errores con tildes
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# Definimos el directorio de trabajo dentro del contenedor
WORKDIR /app

# Corregido: Copiamos todos los archivos del proyecto al contenedor
COPY . .

# Damos permisos de ejecución al wrapper de Maven
RUN chmod +x mvnw

# Compilamos el proyecto saltando los tests para evitar errores de conexión a DB en el build
RUN ./mvnw clean package -Dmaven.test.skip=true

# Renombramos el archivo .jar generado para que sea fácil de ejecutar
RUN cp target/*.jar app.jar

# Comando para iniciar la aplicación
ENTRYPOINT ["java", "-jar", "app.jar"]