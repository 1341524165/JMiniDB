package backend.parser;

import backend.utils.Error;

public class Tokenizer {
    private byte[] stat;
    private int pos;
    private String currentToken;
    private boolean flushToken;
    private Exception err;

    public Tokenizer(byte[] stat) {
        this.stat = stat;
        this.pos = 0;
        this.currentToken = "";
        this.flushToken = true;
    }

    public String peek() throws Exception {
        if (err != null)
            throw err;
        if (flushToken) {
            String token = null;
            try {
                token = next();
            } catch (Exception e) {
                err = e;
                throw e;
            }
            currentToken = token;
            flushToken = false;
        }
        return currentToken;
    }

    public void pop() {
        flushToken = true;
    }

    public byte[] errStat() {
        byte[] res = new byte[stat.length + 3];
        System.arraycopy(stat, 0, res, 0, pos);
        System.arraycopy("<< ".getBytes(), 0, res, pos, 3);
        System.arraycopy(stat, pos, res, pos + 3, stat.length - pos);
        return res;
    }

    private void popByte() {
        pos++;
    }

    private Byte peekByte() {
        return pos == stat.length ? null : stat[pos];
    }

    private String next() throws Exception {
        if (err != null)
            throw err;
        return nextMetaState();
    }

    private String nextMetaState() throws Exception {
        while (true) {
            Byte b = peekByte();
            if (b == null)
                return "";
            if (!isBlank(b))
                break;
            popByte();
        }
        Byte b = peekByte();
        if (isSymbol(b)) {
            popByte();
            return new String(new byte[] { b });
        } else if (b == '"' || b == '\'') {
            return nextQuoteState();
        } else if (isAlphaBeta(b) || isDigit(b)) {
            return nextTokenState();
        } else {
            err = Error.InvalidCommandException;
            throw err;
        }
    }

    private String nextTokenState() {
        StringBuilder sb = new StringBuilder();
        while (true) {
            Byte b = peekByte();
            if (b == null || !(isAlphaBeta(b) || isDigit(b) || b == '_'))
                break;
            sb.append(new String(new byte[] { b }));
            popByte();
        }
        return sb.toString();
    }

    private String nextQuoteState() throws Exception {
        byte quote = peekByte();
        popByte();
        StringBuilder sb = new StringBuilder();
        while (true) {
            Byte b = peekByte();
            if (b == null) {
                err = Error.InvalidCommandException;
                throw err;
            }
            if (b == quote) {
                popByte();
                break;
            }
            sb.append(new String(new byte[] { b }));
            popByte();
        }
        return sb.toString();
    }

    static boolean isDigit(byte b) {
        return b >= '0' && b <= '9';
    }

    static boolean isAlphaBeta(byte b) {
        return (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z');
    }

    static boolean isBlank(byte b) {
        return b == ' ' || b == '\n' || b == '\t';
    }

    static boolean isSymbol(byte b) {
        return b == '>' || b == '<' || b == '=' || b == '*' || b == ',' || b == '(' || b == ')';
    }
}