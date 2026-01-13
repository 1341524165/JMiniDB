// Begin.java
package backend.parser;

public class statement {
    // Begin.java
    public static class Begin {
        public int level;
    }

    // Commit.java
    public static class Commit {
        public long xid;
    }

    // Abort.java
    public static class Abort {
        public long xid;
    }

    // Create.java
    public static class Create {
        public String tableName;
        public String[] fieldName;
        public String[] fieldType;
        public String[] index;
    }

    // Drop.java
    public static class Drop {
        public String tableName;
    }

    // Select.java
    public static class Select {
        public String tableName;
        public String[] fields;
        public Where where;
    }

    // Insert.java
    public static class Insert {
        public String tableName;
        public String[] values;
    }

    // Delete.java
    public static class Delete {
        public String tableName;
        public Where where;
    }

    // Update.java
    public static class Update {
        public String tableName;
        public String fieldName;
        public String value;
        public Where where;
    }

    // Where.java
    public static class Where {
        public SingleExpression singleExp1;
        public String logicOp; // "and" or "or"
        public SingleExpression singleExp2;
    }

    // SingleExpression.java
    public static class SingleExpression {
        public String field;
        public String compareOp; // ">", "<", "="
        public String value;
    }

    // Show.java
    public static class Show {
    }
}