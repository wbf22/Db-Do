import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class Test {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/db_do";
        String username = "postgres";
        String password = "password";

        try {
            Connection connection = DriverManager.getConnection(url, username, password);

            // Perform database operations here
            // pgp('postgres://postgres:password@localhost:5432/bundo_db');

            Statement statement = connection.createStatement();
            String sql = """
                CREATE TABLE IF NOT EXISTS public.sample_table (
                    bigint_col BIGINT,
                    bigserial_col BIGSERIAL,
                    bit_col BIT(10),
                    bit_varying_col BIT VARYING(20),
                    boolean_col BOOLEAN,
                    box_col BOX,
                    bytea_col BYTEA,
                    character_col CHARACTER(50),
                    character_varying_col CHARACTER VARYING(100),
                    cidr_col CIDR,
                    circle_col CIRCLE,
                    date_col DATE,
                    double_precision_col DOUBLE PRECISION,
                    inet_col INET,
                    integer_col INTEGER,
                    interval_col INTERVAL,
                    json_col JSON,
                    jsonb_col JSONB,
                    line_col LINE,
                    lseg_col LSEG,
                    macaddr_col MACADDR,
                    macaddr8_col MACADDR8,
                    money_col MONEY,
                    numeric_col NUMERIC(10, 2),
                    path_col PATH,
                    pg_lsn_col PG_LSN,
                    point_col POINT,
                    polygon_col POLYGON,
                    real_col REAL,
                    smallint_col SMALLINT,
                    smallserial_col SMALLSERIAL,
                    serial_col SERIAL,
                    text_col TEXT,
                    time_col TIME,
                    time_with_zone_col TIME WITH TIME ZONE,
                    timestamp_col TIMESTAMP,
                    timestamp_with_zone_col TIMESTAMP WITH TIME ZONE,
                    tsquery_col TSQUERY,
                    tsvector_col TSVECTOR,
                    txid_snapshot_col TXID_SNAPSHOT,
                    uuid_col UUID,
                    xml_col XML
                );
                    """;;
            statement.execute(sql);
            statement.close();


            statement = connection.createStatement();
            sql = "select * from public.sample_table";
            ResultSet resultSet = statement.executeQuery(sql);
            for (int i = 1; i <= resultSet.getMetaData().getColumnCount(); i++) {
                System.out.println(resultSet.getMetaData().getColumnTypeName(i));
            }


            connection.close(); // Close the connection when done
        } catch (SQLException e) {
            System.err.println("Connection error: " + e.getMessage());
        }
    }    
}
