import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class SmtpProxy {
    
    private static final int PROXY_PORT = 55555;
    private static final String SMTP_SERVER_IP = "127.0.0.1";
    private static final int SMTP_SERVER_PORT = 25;

    public static void main(String[] args) {
        try (ServerSocket proxySocket = new ServerSocket(PROXY_PORT)) {
            while (true) {
                Socket clientSocket = proxySocket.accept();
                try {
                    Socket serverSocket = new Socket(SMTP_SERVER_IP, SMTP_SERVER_PORT);
                    
                    Thread c2s = new Thread(new ProxyTask(clientSocket, serverSocket, true));
                    Thread s2c = new Thread(new ProxyTask(serverSocket, clientSocket, false));
                    
                    c2s.start();
                    s2c.start();
                } catch (Exception e) {
                    clientSocket.close();
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

    @Override
    public void run() {
        try {
            InputStream in = inSocket.getInputStream();
            OutputStream out = outSocket.getOutputStream();
            byte[] buffer = new byte[4096];
            int bytesRead;

            while ((bytesRead = in.read(buffer)) != -1) {
                
                if (isClientToServer) {
                    
                }
                
                out.write(buffer, 0, bytesRead);
                out.flush();
            }
        } catch (Exception e) {
            
        } finally {
            try { inSocket.close(); } catch (Exception e) {}
            try { outSocket.close(); } catch (Exception e) {}
        }
    }
}