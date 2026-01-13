package transport;

import java.io.*;
import java.net.Socket;

/**
 * Transporter 通过 Socket 发送和接收数据
 * 使用十六进制编码避免特殊字符问题
 */
public class Transporter {
    private Socket socket;
    private BufferedReader reader;
    private BufferedWriter writer;

    public Transporter(Socket socket) throws IOException {
        this.socket = socket;
        this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
    }

    /**
     * 发送数据（十六进制编码 + 换行符）
     */
    public void send(byte[] data) throws Exception {
        String raw = hexEncode(data);
        writer.write(raw);
        writer.flush();
    }

    /**
     * 接收数据（读取一行并十六进制解码）
     */
    public byte[] receive() throws Exception {
        String line = reader.readLine();
        if (line == null) {
            close();
        }
        return hexDecode(line);
    }

    /**
     * 关闭连接
     */
    public void close() throws IOException {
        writer.close();
        reader.close();
        socket.close();
    }

    /**
     * 将字节数组编码为十六进制字符串（带换行符）
     */
    private String hexEncode(byte[] buf) {
        StringBuilder sb = new StringBuilder();
        for (byte b : buf) {
            sb.append(String.format("%02x", b));
        }
        sb.append("\n");
        return sb.toString();
    }

    /**
     * 将十六进制字符串解码为字节数组
     */
    private byte[] hexDecode(String buf) {
        if (buf == null || buf.isEmpty()) {
            return new byte[0];
        }
        int len = buf.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(buf.charAt(i), 16) << 4)
                    + Character.digit(buf.charAt(i + 1), 16));
        }
        return data;
    }
}
