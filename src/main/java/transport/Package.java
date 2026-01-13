package transport;

/**
 * Package 是传输的最基本结构
 * 包含数据或错误信息
 */
public class Package {
    private byte[] data;
    private Exception err;

    public Package(byte[] data, Exception err) {
        this.data = data;
        this.err = err;
    }

    public byte[] getData() {
        return data;
    }

    public Exception getErr() {
        return err;
    }
}
