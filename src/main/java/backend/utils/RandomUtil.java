package backend.utils;

import java.security.SecureRandom;
import java.util.Random;

/**
 * 随机数工具类
 * 主要用于生成数据库启动检查时的随机校验码
 */
public class RandomUtil {

    /**
     * 生成指定长度的随机字节数组
     * 
     * @param length 数组长度
     * @return 随机字节数组
     */
    public static byte[] randomBytes(int length) {
        // 使用 SecureRandom 生成强度更高的随机数
        Random r = new SecureRandom();
        byte[] buf = new byte[length];
        r.nextBytes(buf);
        return buf;
    }
}