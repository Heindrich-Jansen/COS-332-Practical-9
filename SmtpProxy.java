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
import java.util.ArrayList;
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

        while (running.get()) {
            
        }
        System.out.println("Shutting down...");
        try {
            proxyThread.join();
            appThread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}

class EmailApplication implements Runnable {
    private static final String EMAIL_SERVER = "raspberrypi.local";

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
            } else if (command.equalsIgnoreCase("exit") || command.equalsIgnoreCase("quit")) {
                SmtpProxy.running.set(false);
            } else {
                System.out.println("Unknown command. Please type 'send', 'view', or 'exit'.");
            }
        }
    }

    private void sendEmail() {
        String sender;
        String recipient;
        String subject;
        String body;
        System.out.println("Enter sender email address:");
        sender = System.console().readLine();
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

            writer.write("MAIL FROM:<" + sender + ">\r\n");
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

    private static final int POP3PORT = 110;

    class Email {
        String sender;
        String subject;
        String body;

        public Email(String sender, String subject, String body) {
            this.sender = sender;
            this.subject = subject;
            this.body = body;
        }

        public String getBody() {
            return body;
        }

        public String getSender() {
            return sender;
        }

        public String getSubject() {
            return subject;
        }
    }

    private void viewEmails() {
        System.out.println("Enter your account name:");
        String email = System.console().readLine();
        System.out.println("Enter your password:");
        String password = System.console().readLine();

        try {
            Socket socket = new Socket(EMAIL_SERVER, POP3PORT);
            System.out.println("Connected to email server: " + EMAIL_SERVER);
            InputStream in = socket.getInputStream();
            BufferedReader reader = new BufferedReader(new java.io.InputStreamReader(in));
            OutputStream out = socket.getOutputStream();

            //LOGIN
            sendPOP3Command(out, reader, "USER " + email, "+OK");
            sendPOP3Command(out, reader, "PASS " + password, "+OK");

            //CHECK FOR NEW EMAILS
            sendPOP3Command(out, reader, "STAT", "+OK");

            // Request the list of all messages
            out.write("LIST\r\n".getBytes());
            out.flush();
            String listResponse = reader.readLine();
            System.out.println("S: " + listResponse);

            // Retrieve each email            
            if (listResponse.startsWith("+OK")) {
                ArrayList<Email> emails = new ArrayList<>();
                ArrayList<Integer> emailNumbers = new ArrayList<>();
                String line = reader.readLine();
                line = reader.readLine();
                System.out.println("Emails: ");
                while (!line.equals(".")) {
                    //System.out.println("S: " + line);
                    String[] parts = line.split(" ");
                    if (parts.length >= 2) {
                        String msgNum = parts[0];
                        emailNumbers.add(Integer.parseInt(msgNum));
                    }
                    line = reader.readLine();
                }

                for (Integer msgNum : emailNumbers) {
                    sendPOP3Command(out, reader, "RETR " + msgNum, "+OK");
                    String emailLine = reader.readLine();
                    String sender = "";
                    String subject = "";
                    String body = "";
                    boolean inBody = false;
                    while (!emailLine.equals(".")) {
                        if (emailLine.startsWith("Return-Path:")) {
                            // Extract sender information
                            sender = emailLine.substring("Return-Path:".length()).trim();
                        } else if (emailLine.startsWith("Subject:")) {
                            // Extract subject information
                            subject = emailLine.substring("Subject:".length()).trim();
                        } else if (emailLine.isEmpty() && !inBody) {
                            // Blank line indicates end of headers, start of body
                            inBody = true;
                        } else if (inBody) {
                            body += emailLine + "\n";
                        }
                        emailLine = reader.readLine();
                    }
                    emails.add(new Email(sender, subject, body));
                    System.out.println(msgNum + ": " + subject + " from " + sender);
                }

                System.out.println("Enter the number of the email you want to read:");
                String choice = System.console().readLine();
                try {
                    int index = Integer.parseInt(choice) - 1;
                    if (index >= 0 && index < emails.size()) {
                        System.out.println("");
                        Email selectedEmail = emails.get(index);
                        System.out.println("From: " + selectedEmail.getSender());
                        System.out.println("Subject: " + selectedEmail.getSubject());
                        System.out.println("Body:\n" + selectedEmail.getBody());
                    } else {
                        System.out.println("Invalid email number.");
                    }
                } catch (NumberFormatException e) {
                    for (Email email2 : emails) {
                        if (email2.getSubject().equals(choice)) {
                            System.out.println("");
                            System.out.println("From: " + email2.getSender());
                            System.out.println("Subject: " + email2.getSubject());
                            System.out.println("Body:\n" + email2.getBody());
                            break;
                        }
                    }
                }
            }

            //LOGOUT
            sendPOP3Command(out, reader, "QUIT", "+OK");

            reader.close();
            in.close();
            out.close();
            socket.close();
        } catch (Exception e) {
            e.printStackTrace();
        }        
    }

    private static void sendPOP3Command(OutputStream out, BufferedReader reader, String cmd, String expectedCode) throws IOException {
        out.write((cmd + "\r\n").getBytes());
        out.flush();
        String response = reader.readLine();
       // System.out.println("S: " + response);
        if (!response.startsWith(expectedCode) && !expectedCode.isEmpty()) {
            throw new IOException("POP3 Error: Expected " + expectedCode + " but got " + response);
        }
    }
}

class ProxyServer implements Runnable {
    private static final int PROXY_PORT = 55555;
    private static final String SMTP_SERVER_IP = "raspberrypi.local";
    private static final int SMTP_SERVER_PORT = 25;

    @Override
    public void run() {
         try (ServerSocket proxySocket = new ServerSocket(PROXY_PORT)) {
            proxySocket.setSoTimeout(1000); // 1 second timeout
            System.out.println("SmtpProxy listening on port " + PROXY_PORT);
            while (SmtpProxy.running.get()) {
                try {
                    Socket clientSocket = proxySocket.accept();
                    try {
                        Socket serverSocket = new Socket(SMTP_SERVER_IP, SMTP_SERVER_PORT);
                        
                        Thread c2s = new Thread(new ProxyTask(clientSocket, serverSocket, true));
                        Thread s2c = new Thread(new ProxyTask(serverSocket, clientSocket, false));
                        
                        c2s.start();
                        s2c.start();
                        if (SmtpProxy.running.get()) {
                            c2s.join();
                            s2c.join();
                        }
                    } catch (Exception e) {
                        try { clientSocket.close(); } catch (IOException ex) {}
                    }
                } catch (java.net.SocketTimeoutException e) {
                    // Timeout occurred, loop will check running flag again
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