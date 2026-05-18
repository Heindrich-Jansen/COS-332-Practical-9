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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SmtpProxy {

    public static AtomicBoolean running = new AtomicBoolean(true);
    public static void main(String[] args) {

        Thread proxyThread = new Thread(new ProxyServer());
        Thread appThread = new Thread(new EmailApplication());

        proxyThread.start();
        appThread.start();
    }
}

class EmailApplication implements Runnable {
    private String recipient;
    private String subject;
    private String body;

    public EmailApplication() {
        
    }

    @Override
    public void run() {
        System.out.println("Welcome to Your Company's Email application!");
        
        while (SmtpProxy.running.get()) {
            System.out.println("Type 'send' to compose and send an email, 'view' to view emails, or 'exit' to quit:");
            String command = System.console().readLine();
            if (command.equalsIgnoreCase("send")) {
                sendEmail();
            } else if (command.equalsIgnoreCase("view")) {
                viewEmails();
            } else if (command.equalsIgnoreCase("exit")) {
                SmtpProxy.running.set(false);
            } else {
                System.out.println("Unknown command. Please type 'send', 'view', or 'exit'.");
            }
        }
    }

    private void sendEmail() {
        System.out.println("Enter recipient email address:");
        recipient = System.console().readLine();
        System.out.println("Enter email subject:");
        subject = System.console().readLine();
        System.out.println("Enter email body (end with a single dot on a line):");
        StringBuilder bodyBuilder = new StringBuilder();
        String line;
        while (!(line = System.console().readLine()).equals(".")) {
            bodyBuilder.append(line).append("\r\n");
        }
        body = bodyBuilder.toString();

        System.out.println("Email composed. Connecting to Proxy Server...");
        try (Socket socket = new Socket("localhost", 55555)) {
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.ISO_8859_1));
            BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.ISO_8859_1));

            // Simple SMTP conversation
            reader.readLine(); // Read server greeting
            writer.write("HELO localhost\r\n");
            writer.flush();
            
            // Read HELO response
            System.out.println(reader.readLine());

            writer.write("MAIL FROM:<" + recipient + ">\r\n");
            writer.flush();
            System.out.println(reader.readLine()); // Read MAIL FROM response
            writer.write("RCPT TO:<" + recipient + ">\r\n");
            writer.flush();
            System.out.println(reader.readLine()); // Read RCPT TO response
            writer.write("DATA\r\n");
            writer.flush();
            System.out.println(reader.readLine()); // Read DATA response
            writer.write("Subject: " + subject + "\r\n");
            writer.write("\r\n"); // Blank line between headers and body
            writer.write(body);
            writer.write(".\r\n"); // End of DATA
            writer.flush();
            System.out.println(reader.readLine()); // Read final response

            writer.write("QUIT\r\n");
            writer.flush();

            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void viewEmails() {
        System.out.println("Viewing emails is not implemented in this demo.");
    }
}

class ProxyServer implements Runnable {
    private static final int PROXY_PORT = 55555;
    private static final String SMTP_SERVER_IP = "raspberrypi.local";
    private static final int SMTP_SERVER_PORT = 25;

    @Override
    public void run() {
         try (ServerSocket proxySocket = new ServerSocket(PROXY_PORT)) {
            System.out.println("SmtpProxy listening on port " + PROXY_PORT);
            while (SmtpProxy.running.get()) {
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
            proxySocket.close();
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
                                writer.write("Hello World.\r\n");
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