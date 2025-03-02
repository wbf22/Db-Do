import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;

import DbDo.ArgParser.HelpException;

class DbDo {


    public static void main(String[] args) {
        Map<String, String> shortArgs = Map.of(
            "-d", 
            """
            Database url with format: 'jdbc:postgresql://<url>:<port>/<db_name>'
            Example 'jdbc:postgresql://localhost:5432/my_db'
            """,
            "-u", 
            """
            Username to connect with
            """,
            "-p", 
            """
            Password to connect with. Be wary of using this flag, as passwords 
            entered this way can be discovered in command history
            """,
            "-s", 
            """
            Script to run. Contains sql intended to be run on the database.
            """
        );


        ArgParser parser = new ArgParser(
            args, 
            shortArgs, 
            null, 
            null, 
            Map.of("-h", "View help", "--help", "View help"), 
            "Db Do!",
            "An app for querying a database using sql files",
            """
            We recommend making a bash script to make calling your favorite db easier.
            On linux and mac you can make a file such as 'db.sh' and stick a command like
            this in the file: 
            
                'java -jar path/to/DbDo.jar -d jdbc:postgresql://localhost:5432/my_db -u my_user -p my_password -s my_sql_script.sql'

            You can then run this command much easier by just running 'sh db.sh' in the terminal. You can then
            spend most of your time just editing queries and viewing the results instead of typing out the 
            command
            """,
            true
        );

        try {
            Map<String, String> parsedArgs = parser.parseArgs();
    
            String url = parsedArgs.get("-d");
            String username = parsedArgs.get("-u");
            String password = parsedArgs.get("-p");
            String script = parsedArgs.get("-s");

            // Perform database operations here
            // pgp('postgres://postgres:password@localhost:5432/bundo_db');
            Connection connection = DriverManager.getConnection(url, username, password);

            Statement statement = connection.createStatement();
            String sql = Files.readString(
                Path.of(script)
            );
            statement.execute(sql);
            ResultSet resultSet = statement.getResultSet();

            if (resultSet != null) {

                // get the length of column names
                int columnLabelLength = 0;
                for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                    int nameLength = resultSet.getMetaData().getColumnName(i).length();
                    if (nameLength > columnLabelLength) columnLabelLength = nameLength;
                }

                // Process the ResultSet
                int biggestRow = 0;
                List<List<Record>> allRecords = new ArrayList<>();
                while (resultSet.next()) {

                    List<Record> records = new ArrayList<>();
                    for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                        String columnName = fitToLength(
                            resultSet.getMetaData().getColumnName(i),
                            columnLabelLength
                        );
                        String columnValue = getColumnValue(resultSet, i).toString();

                        PostgresType type = getColumnType(resultSet, i);
                        int totalLength = columnName.length() + 3 +  columnValue.length();
                        records.add(
                            new Record(
                                type,
                                columnName,
                                columnValue,
                                totalLength
                            )
                        );

                        if (totalLength > biggestRow) biggestRow = totalLength;
                    }
                    allRecords.add(
                        records
                    );
                }

                // pretty print out records
                if (!allRecords.isEmpty()) {
                    // print out pretty
                    int width = getTerminalWidth();
                    int recordsPerRow = width / biggestRow;
                    if (recordsPerRow < 1) recordsPerRow = 1;
                    int desiredLength = (width / recordsPerRow) ;
                    for (int i = 0; i < allRecords.size(); i++) {
                        
                        System.out.println();
                        boolean finishedY = false;
                        int y = 0;
                        while (!finishedY) {

                            finishedY = true;
                            for (int x = 0; x < recordsPerRow; x++) {
                                if (i + x < allRecords.size()) {
                                    List<Record> records = allRecords.get(i + x);
                                    if (y < records.size()) {
                                        // String rowValue = columnName + "   " + columnColor +  columnValue + AnsiControl.RESET;
                                        Record record = records.get(y);
                                        int neededWhitespace = desiredLength - record.totalLength;
                                        if (neededWhitespace < 0) neededWhitespace = 0;
                                        if (x == recordsPerRow - 1) neededWhitespace = 0;
                                        String value = record.columnName + "   " + getColumnColor(record.type) + record.columnValue + AnsiControl.RESET + " ".repeat(neededWhitespace);
                                        System.out.print(value);
                                        // int total = value.length();
                                        finishedY = false;
                                    }
                                    else {
                                        if (x != recordsPerRow - 1)
                                            System.out.print(" ".repeat(desiredLength));

                                    }
                                }
                                else {
                                    System.out.print(" ".repeat(desiredLength));
                                }
                            }
                            System.out.println();
                            y++;
                        }
                        i += recordsPerRow - 1;
                        System.out.println();
        
        
                    }

                }

            }
            else {
                System.out.println();
                System.out.println("NO RESULTS");
                System.out.println();
            }


            connection.close(); // Close the connection when done

        } catch (SQLException | IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
        catch(ArgParser.HelpException e) {}


    
    }

    public static class Record {
        public PostgresType type;
        public String columnName;
        public String columnValue;
        public int totalLength;

        public Record(DbDo.PostgresType type, String columnName, String columnValue, int totalLength) {
            this.type = type;
            this.columnName = columnName;
            this.columnValue = columnValue;
            this.totalLength = totalLength;
        }

        
    }


    public static class ArgParser {

        public static class HelpException extends RuntimeException {}

        public String[] args;
        public Map<String, String> shortFlags;
        public Map<String, String> longFlags;
        public Map<String, String> booleanFlags;
        public Map<String, String> helpFlags;
        public String appName;
        public String appDescription;
        public String notes;
        public boolean showManOnNoArgs;
    
        public ArgParser(
            String[] args, 
            Map<String, String> shortFlags, 
            Map<String, String> longFlags, 
            Map<String, String> booleanFlags, 
            Map<String, String> helpFlags, 
            String appName, 
            String appDescription, 
            String notes,
            boolean showManOnNoArgs
        )
        {
            this.args = args;
            this.shortFlags = shortFlags;
            this.longFlags = longFlags;
            this.booleanFlags = booleanFlags;
            this.helpFlags = helpFlags;
            this.appName = appName;
            this.appDescription = appDescription;
            this.notes = notes;
            this.showManOnNoArgs = showManOnNoArgs;
        }

        private Map<String, String> parseArgs() {
            
            try {

                if (showManOnNoArgs && args.length == 0) throw new HelpException();
    
                Map<String, String> parsedArgs = new HashMap<>();
                for (int i = 0; i < args.length; i++) {
                    String arg = args[i];
                    
                    // short flags
                    if (shortFlags.containsKey(arg)) {
                        i++;
                        String nextValue = args[i];
                        parsedArgs.put(arg, nextValue);
                    }
                    // boolean flags
                    else if (booleanFlags.containsKey(arg)) {
                        parsedArgs.put(arg, "");
                    }
                    // long flags
                    else if(arg.contains("=")) {
                        String[] longArg = arg.split("=");
                        parsedArgs.put(longArg[0], longArg[1]);
                    }
                    else {
    
                        if (!helpFlags.containsKey(arg)) {
                            throw new IllegalArgumentException(arg + " is not a recognized argument");
                        }
                        else {
                            throw new HelpException();
                        }
    
                    }
                }
    
                return parsedArgs;
            }
            catch(Exception e) {
                printMan();
    
                if (e instanceof HelpException) {
                    throw e;
                }
                else {
                    if (e instanceof IllegalArgumentException) throw e;
        
                    throw new IllegalArgumentException("Error parsing args!");

                }
            }
            
    
        }
        
        private void printMan() {
            System.out.println(appName);
            System.out.println();
            System.out.println(appDescription);
            System.out.println();
            System.out.println("Args");
            Map<String, String> allFlags = new LinkedHashMap<>();
            if (booleanFlags != null) allFlags.putAll(booleanFlags);
            if (shortFlags != null) allFlags.putAll(shortFlags);
            if (longFlags != null) allFlags.putAll(longFlags);
            if (helpFlags != null) allFlags.putAll(helpFlags);
            int longestFlagName = allFlags.entrySet().stream()
                .mapToInt(entry -> entry.getKey().length())
                .max()
                .orElse(0);
            for (Entry<String, String> entry : allFlags.entrySet()) {
                String description = "   " + entry.getKey() + " ".repeat(longestFlagName - entry.getKey().length()) + " " + entry.getValue();
                System.out.println(description);
                System.out.println();
            }

            System.out.println(notes);
        }
    }

    private static PostgresType getColumnType(ResultSet resultSet, int index) throws SQLException {
        ResultSetMetaData metaData = resultSet.getMetaData();
        String columnType = metaData.getColumnTypeName(index);
        PostgresType type = PostgresType.valueOf(columnType);
        
        return type;
    }

    private static Object getColumnValue(ResultSet resultSet, int index) throws SQLException {
        PostgresType type = getColumnType(resultSet, index);

        return switch (type) {
            case int8, bigserial -> resultSet.getLong(index);
            case varchar, bpchar, cidr, inet, json, jsonb, macaddr, macaddr8, text, tsquery, tsvector, uuid, xml, date, time, timetz, timestamp, timestamptz -> resultSet.getString(index);
            case bit -> resultSet.getArray(index);
            case varbit -> resultSet.getBlob(index);
            case bool -> resultSet.getBoolean(index);
            case box, interval, line, lseg, circle, path, pg_lsn, point, polygon, txid_snapshot -> resultSet.getObject(index);
            case bytea -> resultSet.getBytes(index);
            case float8 -> resultSet.getDouble(index);
            case int4, int2, smallserial, serial -> resultSet.getInt(index);
            case money, numeric -> resultSet.getBigDecimal(index);
            case float4 -> resultSet.getFloat(index);
            default -> resultSet.getString(index);
        };
    }

    private static String getColumnColor(PostgresType type) throws SQLException {
        return switch (type) {
            case int8, bigserial, float8, money, numeric -> 
                AnsiControl.color(214, 75, 200);
            case varchar, bpchar, cidr, inet, json, jsonb, macaddr, macaddr8, text, tsquery, tsvector, uuid, xml -> 
                AnsiControl.color(75, 214, 84);
            case date, time, timetz, timestamp, timestamptz -> 
                AnsiControl.color(214, 75, 75);
            case bool -> 
                AnsiControl.color(33, 129, 219);
            case int4, int2, smallserial, serial, float4 -> 
                AnsiControl.color(75, 193, 214);
            default -> AnsiControl.color(194, 194, 194);
        };
    }

    public enum PostgresType {
        int8,
        bigserial,
        bit,
        varbit,
        bool,
        box,
        bytea,
        bpchar,
        varchar,
        cidr,
        circle,
        date,
        float8,
        inet,
        int4,
        interval,
        json,
        jsonb,
        line,
        lseg,
        macaddr,
        macaddr8,
        money,
        numeric,
        path,
        pg_lsn,
        point,
        polygon,
        float4,
        int2,
        smallserial,
        serial,
        text,
        time,
        timetz,
        timestamp,
        timestamptz,
        tsquery,
        tsvector,
        txid_snapshot,
        uuid,
        xml;
    }

    private static String fitToLength(String str, int length) {
        if (str.length() > length) {
            return str.substring(0, length);
        }
        else {
            StringBuilder stringBuilder = new StringBuilder(str);
            for(int i = 0; i < length - str.length(); i++) {
                stringBuilder.append(" ");
            }
            return stringBuilder.toString();
        }
    }

    public enum AnsiControl {

        
        RESET("\u001B[0m"),
        CLEAR_SREEN("\u001B[2J\u001B[H"),
        BOLD("\u001B[1m"),
        SIZE("\u001B[=18h"),
        SET_FONT_SIZE("\u001B["),
        HIDE_CURSOR("\u001B[?25l"),
        SHOW_CURSOR("\u001B[?25h");


        
        // public static String CLEAR_SREEN = "\u001B[2J\u001B[H";
        // public static String RESET_FONT_SIZE = "\u001B[0m";
        // public static String SET_FONT_SIZE = "\u001B[";


        private final String code;
        AnsiControl(String code) {
            this.code = code;
        }


        public static void setCursor(int x, int y) {
            System.out.print("\u001B[" + y + ";" + x + "H");
        }

        public static String color(int r, int g, int b) {
            return "\u001B[38;2;" + r + ";" + g + ";" + b + "m";
        }

        public static String color(int x) {
            return "\u001B[38;5;" + x + "m";
        }

        public static String background(int r, int g, int b) {
            return "\u001B[48;2;" + r + ";" + g + ";" + b + "m";
        }

        public static String background(int x) {
            return "\u001B[48;5;" + x + "m";
        }


        @Override
        public String toString() {
            return code;
        }
    }

    public static int getTerminalWidth() {
        int width = 80; // Default width
        try {
            Process process = new ProcessBuilder("sh", "-c", "tput cols 2> /dev/tty").start();
            process.waitFor();
            width = Integer.parseInt(new String(process.getInputStream().readAllBytes()).trim());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return width;
    }

    public static int getTerminalHeight() {
        int height = 24; // Default height
        try {
            Process process = new ProcessBuilder("sh", "-c", "tput lines 2> /dev/tty").start();
            process.waitFor();
            height = Integer.parseInt(new String(process.getInputStream().readAllBytes()).trim());
        } catch (Exception e) {
            e.printStackTrace();
        }
        return height - 1;
    }


}
