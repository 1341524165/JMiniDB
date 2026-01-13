package transport;

import backend.utils.Bytes;
import backend.utils.Error;

import java.util.Arrays;

/**
 * Encoder 负责编码和解码 Package
 * 编码格式：[Flag][data]
 * Flag=0 表示数据，Flag=1 表示错误
 */
public class Encoder {

    /**
     * 将 Package 编码为字节数组
     */
    public byte[] encode(Package pkg) {
        if (pkg.getErr() != null) {
            Exception err = pkg.getErr();
            String msg = "Intern server error!";
            if (err.getMessage() != null) {
                msg = err.getMessage();
            }
            return Bytes.concat(new byte[] { 1 }, msg.getBytes());
        } else {
            return Bytes.concat(new byte[] { 0 }, pkg.getData());
        }
    }

    /**
     * 将字节数组解码为 Package
     */
    public Package decode(byte[] data) throws Exception {
        if (data.length < 1) {
            throw Error.InvalidPkgDataException;
        }
        if (data[0] == 0) {
            return new Package(Arrays.copyOfRange(data, 1, data.length), null);
        } else if (data[0] == 1) {
            return new Package(null, new RuntimeException(new String(Arrays.copyOfRange(data, 1, data.length))));
        } else {
            throw Error.InvalidPkgDataException;
        }
    }
}
