import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmtpProxy {
    
    private static final int PROXY_PORT = 55555;
    private static final String SMTP_SERVER_IP = "127.0.0.1";
    private static final int SMTP_SERVER_PORT = 25;

    public static void main(String[] args) {
        try (ServerSocket proxySocket = new ServerSocket(PROXY_PORT)) {
            System.out.println("SmtpProxy listening on port " + PROXY_PORT);
            while (true) {
                Socket clientSocket = proxySocket.accept();
                try {
                    Socket serverSocket = new Socket(SMTP_SERVER_IP, SMTP_SERVER_PORT);
                    
                    Thread c2s = new Thread(new ProxyTask(clientSocket, serverSocket, true));
                    Thread s2c = new Thread(new ProxyTask(serverSocket, clientSocket, false));
                    
                    c2s.start();
                    s2c.start();
                } catch (Exception e) {
                    try { clientSocket.close(); } catch (IOException ex) {}
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

class ProxyTask implements Runnable {
    private Socket inSocket;
    private Socket outSocket;
    private boolean isClientToServer;

    public ProxyTask(Socket inSocket, Socket outSocket, boolean isClientToServer) {
        this.inSocket = inSocket;
        this.outSocket = outSocket;
        this.isClientToServer = isClientToServer;
    }

    private static final Map<String,String> REPLACEMENTS = createReplacements();

    private static Map<String,String> createReplacements() {
        Map<String,String> m = new HashMap<>();
        m.put("warm", "uncold");
        m.put("bad", "ungood");
        m.put("fast", "speedful");
        m.put("rapid", "speedful");
        m.put("quick", "speedful");
        m.put("slow", "unspeedful");
        m.put("ran", "runned");
        m.put("stole", "stealed");
        m.put("better", "gooder");
        m.put("best", "goodest");
        m.put("very ", "plus");
        return m;
    }

    @Override
    public void run() {
        try {
            if (isClientToServer) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(inSocket.getInputStream(), StandardCharsets.ISO_8859_1));
                BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outSocket.getOutputStream(), StandardCharsets.ISO_8859_1));

                String line;
                boolean dataMode = false;
                boolean illuminatiFound = false;
                StringBuilder dataBuffer = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    if (!dataMode) {
                        writer.write(line + "\r\n");
                        writer.flush();

                        if (line.equalsIgnoreCase("DATA")) {
                            dataMode = true;
                        }
                    } else {
                        // In DATA mode: filter lines, insert disclaimer before terminating dot
                        if (line.equals(".")) {
                            if (illuminatiFound) {
                                writer.write("Hello World");
                            } else {
                                writer.write(dataBuffer.toString());
                            }
                            // send disclaimer to server before terminating the DATA
                            writer.write("Please do not take anything in this email seriously!\r\n");
                            writer.write(".\r\n");
                            writer.flush();
                            dataMode = false;
                        } else {
                            String filtered = filterLine(line);
                            dataBuffer.append(filtered + "\r\n");
                            if (containsIlluminati(filtered)) {
                                illuminatiFound = true;
                            }
                        }
                    }
                }
            } else {
                // Server-to-client: raw copy
                InputStream in = inSocket.getInputStream();
                OutputStream out = outSocket.getOutputStream();
                byte[] buffer = new byte[4096];
                int bytesRead;

                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                    out.flush();
                }
            }
        } catch (Exception e) {
            System.err.println("ProxyTask error: " + e.getMessage());
        } finally {
            try { inSocket.close(); } catch (Exception e) {}
            try { outSocket.close(); } catch (Exception e) {}
        }
    }

    private String filterLine(String line) {
        String result = line;
        for (Map.Entry<String,String> e : REPLACEMENTS.entrySet()) {
            String bad = e.getKey();
            String good = e.getValue();
            String pattern = "(?i)\\b" + Pattern.quote(bad) + "\\b";
            result = result.replaceAll(pattern, Matcher.quoteReplacement(good));
        }
        return result;
    }

    private boolean containsIlluminati(String line) {
        return line.toLowerCase().contains("illuminati");
    }
}