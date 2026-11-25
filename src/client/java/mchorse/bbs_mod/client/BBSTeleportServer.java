package mchorse.bbs_mod.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight local loopback HTTP listener (127.0.0.1:25585) that silently
 * teleports the client player in Minecraft when script coordinate buttons
 * trigger the bbs-tp:// URI handler.
 */
public class BBSTeleportServer
{
    public static final int PORT = 25585;

    private static final Logger LOGGER = LogUtils.getLogger();
    private static ServerSocket serverSocket;
    private static Thread serverThread;
    private static volatile boolean running;

    public static synchronized void start()
    {
        if (running)
        {
            return;
        }

        try
        {
            serverSocket = new ServerSocket(PORT, 16, InetAddress.getByName("127.0.0.1"));
            running = true;
            serverThread = new Thread(BBSTeleportServer::acceptLoop, "BBS-Teleport-Server");
            serverThread.setDaemon(true);
            serverThread.start();
        }
        catch (IOException e)
        {
            LOGGER.warn("Could not start BBS teleport listener on 127.0.0.1:{}", PORT, e);
        }
    }

    public static synchronized void stop()
    {
        running = false;

        if (serverSocket != null)
        {
            try
            {
                serverSocket.close();
            }
            catch (IOException ignored)
            {}

            serverSocket = null;
        }
    }

    private static void acceptLoop()
    {
        while (running && serverSocket != null && !serverSocket.isClosed())
        {
            try (Socket client = serverSocket.accept())
            {
                handleConnection(client);
            }
            catch (IOException e)
            {
                if (running)
                {
                    LOGGER.debug("Teleport server socket error", e);
                }
            }
        }
    }

    private static void handleConnection(Socket socket) throws IOException
    {
        BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        String requestLine = reader.readLine();

        if (requestLine == null || requestLine.isEmpty())
        {
            return;
        }

        String line;
        while ((line = reader.readLine()) != null && !line.isEmpty())
        {}

        int status = 404;
        String body = "Not found";

        String[] parts = requestLine.split(" ");
        if (parts.length >= 2 && parts[1].startsWith("/tp"))
        {
            Map<String, String> params = parseQuery(parts[1]);

            try
            {
                double x = Double.parseDouble(params.getOrDefault("x", ""));
                double y = Double.parseDouble(params.getOrDefault("y", ""));
                double z = Double.parseDouble(params.getOrDefault("z", ""));

                teleportSilently(x, y, z);
                status = 200;
                body = "OK";
            }
            catch (NumberFormatException ignored)
            {
                status = 400;
                body = "Invalid coordinates";
            }
        }

        byte[] payload = body.getBytes(StandardCharsets.UTF_8);
        String headers = "HTTP/1.1 " + status + " " + (status == 200 ? "OK" : "Bad Request") + "\r\n"
            + "Access-Control-Allow-Origin: *\r\n"
            + "Content-Type: text/plain; charset=utf-8\r\n"
            + "Content-Length: " + payload.length + "\r\n"
            + "Connection: close\r\n\r\n";

        OutputStream out = socket.getOutputStream();
        out.write(headers.getBytes(StandardCharsets.US_ASCII));
        out.write(payload);
        out.flush();
    }

    private static Map<String, String> parseQuery(String target)
    {
        Map<String, String> params = new HashMap<>();
        int q = target.indexOf('?');

        if (q < 0 || q + 1 >= target.length())
        {
            return params;
        }

        for (String pair : target.substring(q + 1).split("&"))
        {
            int eq = pair.indexOf('=');

            if (eq > 0)
            {
                String k = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                String v = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);

                params.put(k, v);
            }
        }

        return params;
    }

    private static void teleportSilently(double x, double y, double z)
    {
        MinecraftClient client = MinecraftClient.getInstance();

        if (client == null)
        {
            return;
        }

        client.execute(() ->
        {
            if (client.player == null)
            {
                return;
            }

            double tx = x + 0.5D;
            double ty = y;
            double tz = z + 0.5D;

            client.player.setPosition(tx, ty, tz);

            if (client.player.networkHandler != null)
            {
                String cmd = String.format(Locale.ROOT, "tp @s %.2f %.2f %.2f", tx, ty, tz);
                client.player.networkHandler.sendChatCommand(cmd);
            }
        });
    }
}
