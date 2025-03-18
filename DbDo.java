import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;


class DbDo {

    private static int EXTRA_SPACE_BEFORE_VALUE = 3;

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
            Map.of("--tables", "Print out all schemas and tables in the database"), 
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

            // print out table definitions if present
            if (parsedArgs.containsKey("--tables")) {
                printSchemas(url, username, password);
            }

            // run any user provided scripts
            if (script != null) {

                // Perform database operations here
                Connection connection = DriverManager.getConnection(url, username, password);

                Statement statement = connection.createStatement();
                String sql = Files.readString(
                    Path.of(script)
                );
                statement.execute(sql);
                ResultSet resultSet = statement.getResultSet();

                if (resultSet != null) {

                    // Process the ResultSet
                    List<List<Record>> allRecords = new ArrayList<>();
                    while (resultSet.next()) {

                        List<Record> records = new ArrayList<>();
                        for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                            String columnName = resultSet.getMetaData().getColumnName(i);
                            Object colValue = getColumnValue(resultSet, i);
                            String columnValue = colValue == null ? "null" : colValue.toString();

                            PostgresType type = getColumnType(resultSet, i);
                            records.add(
                                new Record(
                                    type,
                                    columnName,
                                    columnValue
                                )
                            );

                        }
                        allRecords.add(
                            records
                        );
                    }

                    // pretty print out records
                    prettyPrintRecords(allRecords);

                }

                connection.close(); // Close the connection when done
            }


        } 
        catch (SQLException | IOException e) {
            System.err.println("Error: " + e);
        }
        catch(ArgParser.HelpException e) {}


    
    }

    private static void prettyPrintRecords(List<List<Record>> allRecords) throws SQLException {

        // pretty print out records
        if (!allRecords.isEmpty()) {

            // get biggest column name
            int columnLabelLength = allRecords.stream()
                .flatMap(recordList -> recordList.stream())
                .mapToInt(record -> record.columnName.length())
                .max()
                .orElse(0);

            // add spacing to column names
            Map<String, String> columnNamesWithSpacing = allRecords.stream()
                .flatMap(recordList -> recordList.stream())
                .collect(
                    Collectors.toMap(
                        record -> record.columnName, 
                        record -> fitToLength(record.columnName, columnLabelLength),
                        (existingValue, newValue) -> newValue // Merge function to handle duplicate keys
                    )
                );

            // get the biggest row
            int biggestRow = allRecords.stream()
                .mapToInt(records -> records.stream()
                    .mapToInt(record -> columnNamesWithSpacing.get(record.columnName).length() + record.columnValue.length() + EXTRA_SPACE_BEFORE_VALUE)
                    .max()
                    .orElse(0)
                )
                .max()
                .orElse(0);

            // print out pretty
            int width = getTerminalWidth();
            int numColumns = width / biggestRow;
            if (numColumns < 1) numColumns = 1;
            int desiredLength = (width / numColumns);
            int recordsPerColumn = (int) Math.ceil(allRecords.size() / (double) numColumns);

            // sort into columns
            List<List<List<Record>>> columns = new ArrayList<>();
            int lastIndex = 0;
            for (int x = 0; x < numColumns; x++) {
                List<List<Record>> column = new ArrayList<>();
                int nextIndex = recordsPerColumn + lastIndex;
                if (nextIndex > allRecords.size()) nextIndex = allRecords.size();
                columns.add(
                    allRecords.subList(lastIndex, nextIndex)
                );
                lastIndex = nextIndex;
            }
            int y = 0;
            while (y < recordsPerColumn) {
                
                System.out.println();
                boolean finishedY = false;
                int tableRow = 0;
                while (!finishedY) {

                    finishedY = true;
                    for (int x = 0; x < numColumns; x++) {
                        if (x < columns.size() && y < columns.get(x).size()) {
                            List<Record> records = columns.get(x).get(y);

                            if (tableRow < records.size()) {
                                Record record = records.get(tableRow);
                                String columnName = columnNamesWithSpacing.get(record.columnName);

                                // determine whitespace
                                int totalLength = columnName.length() + EXTRA_SPACE_BEFORE_VALUE +  record.columnValue.length();
                                int neededWhitespace = desiredLength - totalLength;
                                if (neededWhitespace < 0) neededWhitespace = 0;
                                if (x == numColumns - 1) neededWhitespace = 0;

                                // determine colors
                                String columnNameColor = record.columnNameColorOverride != null ? record.columnNameColorOverride : AnsiControl.RESET.toString();
                                String columnValueColor = record.columnValueColorOverride != null ? record.columnValueColorOverride : getColumnColor(record.type, record.columnValue);

                                String value = columnNameColor + columnName + " ".repeat(EXTRA_SPACE_BEFORE_VALUE) + columnValueColor + record.columnValue + AnsiControl.RESET + " ".repeat(neededWhitespace);
                                System.out.print(value);
                                // int total = value.length();
                                finishedY = false;
                            }
                            else {
                                if (x != numColumns - 1)
                                    System.out.print(" ".repeat(desiredLength));

                            }
                        }
                        else {
                            System.out.print(" ".repeat(desiredLength));
                        }
                    }
                    System.out.println();
                    tableRow++;
                }
                y++;
                System.out.println();


            }

        }
        else {
            System.out.println();
            System.out.println("NO RESULTS");
            System.out.println();
        }
    }

    public static void printSchemas(String url, String username, String password) {

        try {
            
            // Perform database operations here
            Connection connection = DriverManager.getConnection(url, username, password);

            Statement statement = connection.createStatement();
            String sql = """
                SELECT schema_name
                FROM information_schema.schemata;
            """;
            statement.execute(sql);
            ResultSet resultSet = statement.getResultSet();

            if (resultSet != null) {

                // get schema names
                List<String> schemaNames = new ArrayList<>();
                while (resultSet.next()) {
                    schemaNames.add(resultSet.getString(1));
                }
                schemaNames = schemaNames.stream()
                    .filter(name -> !name.startsWith("pg_"))
                    .filter(name -> !name.equals("information_schema"))
                    .toList();

                for (String schema : schemaNames) {
                    System.out.println();
                    System.out.println();
                    System.out.println(AnsiControl.color(137, 49, 140) + "SCHEMA " + schema + AnsiControl.RESET);

                    // get tables for the schema
                    sql = """
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE table_schema = ?;
                    """;
                    PreparedStatement pStatement = connection.prepareStatement(sql);
                    pStatement.setString(1, schema);
                    resultSet = pStatement.executeQuery();
                    List<String> tableNames = new ArrayList<>();
                    while (resultSet.next()) {
                        tableNames.add(resultSet.getString(1));
                    }

                    // print out table definitions
                    List<List<Record>> allRecords = new ArrayList<>();
                    for (String table : tableNames) {

                        List<Record> rows = new ArrayList<>();
                        Record tableTitle = new Record(
                            PostgresType.text,
                            "TABLE " + schema + "." + table,
                            ""
                        );
                        tableTitle.columnNameColorOverride = AnsiControl.color(51, 56, 189);
                        rows.add(tableTitle);
                        
                        sql = """
                            SELECT column_name, column_default, is_nullable, udt_name, character_maximum_length
                            FROM information_schema.columns
                            WHERE table_name = ?;
                        """;
                        pStatement = connection.prepareStatement(sql);
                        pStatement.setString(1, table);
                        resultSet = pStatement.executeQuery();
                        while (resultSet.next()) {
                            // get column info
                            String type = resultSet.getString(4);
                            String columnName = resultSet.getString(1);
                            String columnValue = type;
                            
                            // set max length for varchars
                            int maxLength = resultSet.getInt(5);
                            if (maxLength > 0) {
                                columnValue += "(" + maxLength + ")";
                            }

                            // set nullable
                            String defaultValue = resultSet.getString(2);
                            boolean isNullable = resultSet.getString(3).equals("YES");
                            if (isNullable) {
                                columnValue += " NULL";
                            }
                            else {
                                columnValue += " NOT NULL";
                            }

                            // set default value
                            if (defaultValue != null) {
                                columnValue += " DEFAULT " + defaultValue;
                            }

                            // add commas
                            columnValue += ",";

                            Record column = new Record(
                                PostgresType.valueOf(type),
                                columnName,
                                columnValue
                            );
                            rows.add(column);
                        }
                        
                        allRecords.add(rows);

                        System.out.println();
                    }

                    prettyPrintRecords(allRecords);
                }


            }
            else {
                System.out.println();
                System.out.println("NO RESULTS");
                System.out.println();
            }


            connection.close(); // Close the connection when done

        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
        }
        catch(ArgParser.HelpException e) {}

    }

    public static class Record {
        public PostgresType type;
        public String columnName;
        public String columnValue;

        public String columnNameColorOverride;
        public String columnValueColorOverride;

        public Record(DbDo.PostgresType type, String columnName, String columnValue) {
            this.type = type;
            this.columnName = columnName;
            this.columnValue = columnValue;
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

    private static String getColumnColor(PostgresType type, Object value) throws SQLException {
        if (value == null || "null".equals(value)) return AnsiControl.color(38, 38, 38);

        return switch (type) {
            case int8, bigserial, float8, money, numeric -> 
                AnsiControl.color(214, 75, 200);
            case varchar, bpchar, cidr, inet, json, jsonb, macaddr, macaddr8, text, tsquery, tsvector, uuid, xml, name, _text -> 
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
        xml,
        geography,
        name,
        _text;
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
