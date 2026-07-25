package aoharureverie.ocaacrclient.oldchat.api;

import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URI;
import java.security.MessageDigest;
import java.security.SecureRandom;
import javax.net.ssl.SSLSocketFactory;

public class SimpleWebSocketClient {
    public interface Listener {
        void onOpen();
        void onMessage(String message);
        void onClose(int code, String reason);
        void onError(Exception ex);
    }

    private static final String WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private final URI uri;
    private final Listener listener;
    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private Thread readerThread;
    private volatile boolean running;

    public SimpleWebSocketClient(URI uri, Listener listener) {
        this.uri = uri;
        this.listener = listener;
    }

    public synchronized void connect() {
        if (readerThread != null) {
            if (readerThread.isAlive()) {
                return;
            }
            readerThread = null;
        }
        readerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                runLoop();
            }
        }, "ws-reader");
        readerThread.start();
    }

    public void close() {
        running = false;
        try {
            sendFrame(0x8, new byte[0]);
        } catch (Exception e) {
        }
        closeInternal();
    }

    private void runLoop() {
        try {
            openSocket();
            handshake();
            running = true;
            if (listener != null) {
                listener.onOpen();
            }
            readFrames();
        } catch (Exception e) {
            if (listener != null) {
                listener.onError(e);
            }
        } finally {
            running = false;
            closeInternal();
            synchronized (this) {
                readerThread = null;
            }
            if (listener != null) {
                listener.onClose(0, "");
            }
        }
    }

    private void openSocket() throws Exception {
        String scheme = uri.getScheme();
        String host = uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            port = "wss".equalsIgnoreCase(scheme) ? 443 : 80;
        }
        if ("wss".equalsIgnoreCase(scheme)) {
            socket = SSLSocketFactory.getDefault().createSocket(host, port);
        } else {
            socket = new Socket(host, port);
        }
        in = socket.getInputStream();
        out = socket.getOutputStream();
    }

    private void handshake() throws Exception {
        String path = uri.getRawPath();
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        if (uri.getRawQuery() != null && !uri.getRawQuery().isEmpty()) {
            path += "?" + uri.getRawQuery();
        }
        String host = uri.getHost();
        int port = uri.getPort();
        if (port == -1) {
            port = "wss".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
        }

        byte[] keyBytes = new byte[16];
        new SecureRandom().nextBytes(keyBytes);
        String key = Base64.encodeToString(keyBytes, Base64.NO_WRAP);

        StringBuilder req = new StringBuilder();
        req.append("GET ").append(path).append(" HTTP/1.1\r\n");
        req.append("Host: ").append(host).append(":").append(port).append("\r\n");
        req.append("Upgrade: websocket\r\n");
        req.append("Connection: Upgrade\r\n");
        req.append("Sec-WebSocket-Key: ").append(key).append("\r\n");
        req.append("Sec-WebSocket-Version: 13\r\n\r\n");
        out.write(req.toString().getBytes("UTF-8"));
        out.flush();

        byte[] headerBytes = readHeader(in);
        String header = new String(headerBytes, "UTF-8");
        if (!header.startsWith("HTTP/1.1 101")) {
            String statusLine = header.split("\r\n")[0];
            throw new Exception("handshake failed: " + statusLine);
        }
        String accept = findHeader(header, "Sec-WebSocket-Accept");
        String expected = computeAccept(key);
        if (accept == null || !accept.trim().equals(expected)) {
            throw new Exception("bad accept");
        }
    }

    private byte[] readHeader(InputStream in) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] last = new byte[4];
        int count = 0;
        while (true) {
            int b = in.read();
            if (b == -1) {
                throw new Exception("eof");
            }
            baos.write(b);
            last[count % 4] = (byte) b;
            count++;
            if (count >= 4 && last[(count - 4) % 4] == '\r' && last[(count - 3) % 4] == '\n'
                    && last[(count - 2) % 4] == '\r' && last[(count - 1) % 4] == '\n') {
                break;
            }
        }
        return baos.toByteArray();
    }

    private String findHeader(String header, String key) {
        String[] lines = header.split("\r\n");
        for (String line : lines) {
            int idx = line.indexOf(':');
            if (idx > 0) {
                String name = line.substring(0, idx).trim();
                if (name.equalsIgnoreCase(key)) {
                    return line.substring(idx + 1).trim();
                }
            }
        }
        return null;
    }

    private String computeAccept(String key) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        md.update((key + WS_GUID).getBytes("UTF-8"));
        byte[] sha1 = md.digest();
        return Base64.encodeToString(sha1, Base64.NO_WRAP);
    }

    private void readFrames() throws Exception {
        while (running) {
            int b1 = in.read();
            if (b1 == -1) {
                break;
            }
            int b2 = in.read();
            if (b2 == -1) {
                break;
            }
            int opcode = b1 & 0x0F;
            boolean masked = (b2 & 0x80) != 0;
            long length = b2 & 0x7F;
            if (length == 126) {
                length = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
            } else if (length == 127) {
                long l = 0;
                for (int i = 0; i < 8; i++) {
                    l = (l << 8) | (in.read() & 0xFF);
                }
                length = l;
            }
            byte[] mask = null;
            if (masked) {
                mask = new byte[4];
                readFully(mask, 4);
            }
            byte[] payload = new byte[(int) length];
            readFully(payload, (int) length);
            if (masked && mask != null) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] = (byte) (payload[i] ^ mask[i % 4]);
                }
            }
            if (opcode == 0x1) {
                String msg = new String(payload, "UTF-8");
                if (listener != null) {
                    listener.onMessage(msg);
                }
            } else if (opcode == 0x8) {
                sendFrame(0x8, new byte[0]);
                break;
            } else if (opcode == 0x9) {
                sendFrame(0xA, payload);
            } else {
                // ignore
            }
        }
    }

    private void readFully(byte[] buffer, int length) throws Exception {
        int offset = 0;
        while (offset < length) {
            int read = in.read(buffer, offset, length - offset);
            if (read == -1) {
                throw new Exception("eof");
            }
            offset += read;
        }
    }

    private synchronized void sendFrame(int opcode, byte[] payload) throws Exception {
        if (out == null) {
            return;
        }
        int length = payload.length;
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x80 | (opcode & 0x0F));
        if (length <= 125) {
            frame.write(0x80 | length);
        } else if (length <= 0xFFFF) {
            frame.write(0x80 | 126);
            frame.write((length >> 8) & 0xFF);
            frame.write(length & 0xFF);
        } else {
            frame.write(0x80 | 127);
            long l = length;
            for (int i = 7; i >= 0; i--) {
                frame.write((int) ((l >> (8 * i)) & 0xFF));
            }
        }
        byte[] mask = new byte[4];
        new SecureRandom().nextBytes(mask);
        frame.write(mask);
        byte[] masked = new byte[payload.length];
        for (int i = 0; i < payload.length; i++) {
            masked[i] = (byte) (payload[i] ^ mask[i % 4]);
        }
        frame.write(masked);
        out.write(frame.toByteArray());
        out.flush();
    }

    private void closeInternal() {
        try {
            if (in != null) {
                in.close();
            }
        } catch (Exception e) {
        }
        try {
            if (out != null) {
                out.close();
            }
        } catch (Exception e) {
        }
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (Exception e) {
        }
        in = null;
        out = null;
        socket = null;
    }
}
