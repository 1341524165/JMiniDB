package client;

import transport.Package;
import transport.Packager;

/**
 * RoundTripper 实现单次收发动作
 */
public class RoundTripper {
    private Packager packager;

    public RoundTripper(Packager packager) {
        this.packager = packager;
    }

    /**
     * 发送并接收一次往返
     */
    public Package roundTrip(Package pkg) throws Exception {
        packager.send(pkg);
        return packager.receive();
    }

    /**
     * 关闭连接
     */
    public void close() throws Exception {
        packager.close();
    }
}
