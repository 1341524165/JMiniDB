package client;

import transport.Package;

/**
 * Client 执行 SQL 语句并返回结果
 */
public class Client {
    private RoundTripper rt;

    public Client(RoundTripper rt) {
        this.rt = rt;
    }

    /**
     * 执行 SQL 语句
     */
    public byte[] execute(byte[] stat) throws Exception {
        Package pkg = new Package(stat, null);
        Package resPkg = rt.roundTrip(pkg);
        if (resPkg.getErr() != null) {
            throw resPkg.getErr();
        }
        return resPkg.getData();
    }

    /**
     * 关闭连接
     */
    public void close() throws Exception {
        rt.close();
    }
}
