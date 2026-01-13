package client;

import transport.Encoder;
import transport.Packager;
import transport.Transporter;

import java.io.IOException;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * 客户端启动入口
 */
public class Launcher {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 9999;

    public static void main(String[] args) throws UnknownHostException, IOException {
        String host = DEFAULT_HOST;
        int port = DEFAULT_PORT;

        // 解析命令行参数
        for (int i = 0; i < args.length; i++) {
            if ("-host".equals(args[i]) && i + 1 < args.length) {
                host = args[++i];
            } else if ("-port".equals(args[i]) && i + 1 < args.length) {
                port = Integer.parseInt(args[++i]);
            }
        }

        Socket socket = new Socket(host, port);
        Encoder e = new Encoder();
        Transporter t = new Transporter(socket);
        Packager packager = new Packager(t, e);

        Client client = new Client(new RoundTripper(packager));
        Shell shell = new Shell(client);
        shell.run();
    }
}
