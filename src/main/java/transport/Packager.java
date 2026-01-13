package transport;

/**
 * Packager 是 Encoder 和 Transporter 的组合
 * 对外提供 send 和 receive 方法
 */
public class Packager {
    private Transporter transporter;
    private Encoder encoder;

    public Packager(Transporter transporter, Encoder encoder) {
        this.transporter = transporter;
        this.encoder = encoder;
    }

    /**
     * 发送 Package
     */
    public void send(Package pkg) throws Exception {
        byte[] data = encoder.encode(pkg);
        transporter.send(data);
    }

    /**
     * 接收 Package
     */
    public Package receive() throws Exception {
        byte[] data = transporter.receive();
        return encoder.decode(data);
    }

    /**
     * 关闭连接
     */
    public void close() throws Exception {
        transporter.close();
    }
}
