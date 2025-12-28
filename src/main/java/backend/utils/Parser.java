package backend.utils;

import java.nio.ByteBuffer;

public class Parser {
    // ------------------- long -------------------
    /**
     * 将 byte[] 转换为 long
     * 
     * @param array 字节数组 (至少8位)
     * @return 解析出的 long 值
     */
    public static long parseLong(byte[] array) {
        ByteBuffer buffer = ByteBuffer.wrap(array, 0, 8);
        return buffer.getLong();
    }

    /**
     * 将 long 转换为 byte[]
     * 
     * @param value long 值
     * @return 长度为 8 的字节数组
     */
    public static byte[] long2Byte(long value) {
        return ByteBuffer.allocate(Long.SIZE / Byte.SIZE).putLong(value).array();
    }

    // ------------------- short -------------------
    /**
     * 将 byte[] 转换为 short
     * 
     * @param array 字节数组 (至少2位)
     * @return 解析出的 short 值
     */
    public static short parseShort(byte[] array) {
        ByteBuffer buffer = ByteBuffer.wrap(array, 0, 2);
        return buffer.getShort();
    }

    /**
     * 将 short 转换为 byte[]
     * 
     * @param value short 值
     * @return 长度为 2 的字节数组
     */
    public static byte[] short2Byte(short value) {
        return ByteBuffer.allocate(Short.SIZE / Byte.SIZE).putShort(value).array();
    }
}