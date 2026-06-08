import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.BufferedReader;
import java.net.InetSocketAddress;
import java.io.File;
import java.io.FileInputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

public class ServidorDescargas {

    public static void main(String[] args) throws Exception {
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/descargar", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) throws IOException {

                // Conseguir y limpiar la URL de YouTube que manda el móvil
                String query = exchange.getRequestURI().getQuery();
                if (query == null || !query.contains("url=")) {
                    enviarError(exchange, "Falta el parámetro 'url'");
                    return;
                }

                String urlYoutube = query.split("url=")[1];
                
                urlYoutube = URLDecoder.decode(urlYoutube, StandardCharsets.UTF_8.name());

                System.out.println("Petición recibida para: " + urlYoutube);

                try {
                    
                    String tituloCancion = obtenerTituloVideo(urlYoutube);
                    System.out.println("Título detectado: " + tituloCancion);


                    String nombreArchivoLimpio = tituloCancion.replaceAll("[\\\\/:*?\"<>|]", "_") + ".mp3";

                    
                    String rutaDestinoCompleta = "/tmp/" + nombreArchivoLimpio;

               
                    ejecutarYtdlp(urlYoutube, rutaDestinoCompleta);

                    File mp3 = new File(rutaDestinoCompleta);
                    if (!mp3.exists()) {
                        throw new IOException("El archivo MP3 no se generó correctamente.");
                    }

                 
                    exchange.getResponseHeaders().set("Content-Type", "audio/mpeg");
                    exchange.getResponseHeaders().set("Content-Disposition", "attachment; filename=\"" + nombreArchivoLimpio + "\"");
                    exchange.sendResponseHeaders(200, mp3.length());

                    OutputStream os = exchange.getResponseBody();
                    FileInputStream fis = new FileInputStream(mp3);
                    byte[] buffer = new byte[1024];
                    int count;
                    while ((count = fis.read(buffer)) != -1) {
                        os.write(buffer, 0, count);
                    }
                    fis.close();
                    os.close();

                    System.out.println("¡Archivo [" + nombreArchivoLimpio + "] enviado con éxito!");

                 
                    if (mp3.exists()) {
                        boolean borrado = mp3.delete();
                        if (borrado) {
                            System.out.println("Espacio liberado: Archivo temporal eliminado de la nube.");
                        } else {
                            System.out.println("No se pudo eliminar el archivo temporal.");
                        }
                    }

                } catch (Exception e) {
                    e.printStackTrace();
                    enviarError(exchange, "Hubo un error en el proceso: " + e.getMessage());
                }
            }
        });

        server.setExecutor(null);
        System.out.println("Servidor activo en el puerto " + port + "... Esperando peticiones.");
        server.start();
    }

    // Método que le pregunta a yt-dlp el nombre del vide sin descargarlo para despyues cambiar el nombre del archvbo
    private static String obtenerTituloVideo(String url) throws Exception {
        String[] comando = {"yt-dlp", "--get-title", url};
        Process proceso = new ProcessBuilder(comando).start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(proceso.getInputStream()));
        String titulo = reader.readLine();
        proceso.waitFor();

        if (titulo == null || titulo.trim().isEmpty()) {
            return "cancion_desconocida";
        }
        return titulo.trim();
    }

    private static void ejecutarYtdlp(String url, String rutaDestino) throws Exception {
        String[] comando = {
                "yt-dlp",
                "-x",
                "--audio-format", "mp3",
                "--no-cache-dir",
                "--buffer-size", "16K",
                "--impersonate", "safari",      
                "--no-check-certificates",
                "-o", rutaDestino,
                url
        };

        ProcessBuilder pb = new ProcessBuilder(comando);
        pb.redirectErrorStream(true);

        Process proceso = pb.start();

        try (BufferedReader in = new BufferedReader(new InputStreamReader(proceso.getInputStream()))) {
            String line;
            while ((line = in.readLine()) != null) {
                System.out.println("[yt-dlp] " + line);
            }
        }

        proceso.waitFor();
    }

    private static void enviarError(HttpExchange exchange, String mensaje) throws IOException {
        exchange.sendResponseHeaders(500, mensaje.length());
        OutputStream os = exchange.getResponseBody();
        os.write(mensaje.getBytes());
        os.close();
    }
}
