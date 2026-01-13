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

    // ------------------- int -------------------
    /**
     * 将 byte[] 转换为 int
     * 
     * @param array 字节数组 (至少4位)
     * @return 解析出的 int 值
     */
    public static int parseInt(byte[] array) {
        ByteBuffer buffer = ByteBuffer.wrap(array, 0, 4);
        return buffer.getInt();
    }

    /**
     * 将 int 转换为 byte[]
     * 
     * @param value int 值
     * @return 长度为 4 的字节数组
     */
    public static byte[] int2Byte(int value) {
        return ByteBuffer.allocate(Integer.SIZE / Byte.SIZE).putInt(value).array();
    }

    // ------------------- string -------------------
    /**
     * 将字符串转换为 byte[]
     * 格式：[StringLength(4字节)][StringData]
     */
    public static byte[] string2Byte(String str) {
        byte[] strBytes = str.getBytes();
        byte[] lenBytes = int2Byte(strBytes.length);
        byte[] result = new byte[4 + strBytes.length];
        System.arraycopy(lenBytes, 0, result, 0, 4);
        System.arraycopy(strBytes, 0, result, 4, strBytes.length);
        return result;
    }

    /**
     * 从 byte[] 解析字符串
     * 返回 ParseStringRes，包含解析的字符串和下一个位置偏移
     */
    public static backend.tbm.ParseStringRes parseString(byte[] raw) {
        int len = parseInt(raw);
        String str = new String(raw, 4, len);
        backend.tbm.ParseStringRes res = new backend.tbm.ParseStringRes();
        res.str = str;
        res.next = 4 + len;
        return res;
    }

    /**
     * 将字符串转换为 UID（用于索引）
     * 简单实现：取前8字节的哈希
     */
    public static long str2Uid(String str) {
        long seed = 13331;
        long res = 0;
        for (byte b : str.getBytes()) {
            res = res * seed + (long) b;
        }
        return res;
    }
}