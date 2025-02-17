package worker;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.exceptions.JedisConnectionException;
import java.sql.*;
import org.json.JSONObject;

class Worker {
  public static void main(String[] args) {
    Jedis redis;
    Connection dbConn;
    try {
      try {
        redis = connectToRedis(System.getenv("REDIS_HOSTNAME"));
        dbConn = connectToDB(System.getenv("POSTGRES_DNS"));
      }
      catch (Exception e) {
        redis = connectToRedis("redis");
        dbConn = connectToDB("db");
      }

        System.err.println("Watching vote queue");

        while (true) {
            String voteJSON = redis.blpop(0, "votes").get(1);
            JSONObject voteData = new JSONObject(voteJSON);
            String voterID = voteData.getString("voter_id");
            String vote = voteData.getString("vote");

            System.err.printf("Processing vote for '%s' by '%s'\n", vote, voterID);
            updateVote(dbConn, voterID, vote);
        }
    } catch (SQLException e) {
      e.printStackTrace();
      System.exit(1);
    }
  }

  static void updateVote(Connection dbConn, String voterID, String vote) throws SQLException {
    PreparedStatement insert = dbConn.prepareStatement(
      "INSERT INTO votes (id, vote) VALUES (?, ?)");
    insert.setString(1, voterID);
    insert.setString(2, vote);

    try {
      insert.executeUpdate();
    } catch (SQLException e) {
      PreparedStatement update = dbConn.prepareStatement(
        "UPDATE votes SET vote = ? WHERE id = ?");
      update.setString(1, vote);
      update.setString(2, voterID);
      update.executeUpdate();
    }
  }

  static Jedis connectToRedis(String host) {
    Jedis conn = new Jedis(host);

    while (true) {
      try {
        conn.keys("*");
        break;
      } catch (JedisConnectionException e) {
        System.err.println("Waiting for redis");
        sleep(1000);
      }
    }

    System.err.println("Connected to redis");
    return conn;
  }

  static Connection connectToDB(String host) throws SQLException {
    Connection conn = null;

    try {

      Class.forName("org.postgresql.Driver");
      String url = "jdbc:postgresql://" + host + "/" + System.getenv("POSTGRES_DB");

      while (conn == null) {
        try {
          try {
              conn = DriverManager.getConnection(url, System.getenv("POSTGRES_USER"), System.getenv("POSTGRES_PASSWORD"));
          } catch (SQLException e) {
            System.err.println("Waiting for db");
            sleep(1000);
          }
      } catch(Exception e) {
          try {
            conn = DriverManager.getConnection(url, "postgres", "password");
          } catch (SQLException err) {
            System.err.println("Waiting for db");
            sleep(1000);
          }
        }
      }

      PreparedStatement st = conn.prepareStatement(
        "CREATE TABLE IF NOT EXISTS votes (id VARCHAR(255) NOT NULL UNIQUE, vote VARCHAR(255) NOT NULL)");
      st.executeUpdate();

    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      System.exit(1);
    }

    System.err.println("Connected to db");
    return conn;
  }

  static void sleep(long duration) {
    try {
      Thread.sleep(duration);
    } catch (InterruptedException e) {
      System.exit(1);
    }
  }
}
