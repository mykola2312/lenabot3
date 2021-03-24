package lenabot3.bot.service;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import com.google.gson.JsonObject;

import lenabot3.bot.Service;
import lenabot3.util.JsonData;

public class DataBase extends Service {
	public static final String DATABASE_SERVICE = "DataBase";
	
	private Connection connection;
	
	public DataBase() {
		super("DataBase");
	}
	
	public boolean load() {
		if (connection != null) return true;
		
		JsonObject config = JsonData.loadJsonFile("database.json");
		if (config == null) {
			System.err.println("Service(DataBase).load: database.json not found!");
			return false;
		}
		
		if (config.has("sqlite")) {
			JsonObject sqlite = config.get("sqlite").getAsJsonObject();
			if (!sqlite.has("file")) {
				System.err.println(
					"Service(DataBase).load: object \"sqlite\" must have field \"file\"!\n");
				return false;
			}
			
			try {
				Class.forName("org.sqlite.JDBC");
				
				connection = DriverManager.getConnection(
						"jdbc:sqlite:" + sqlite.get("file").getAsString());
			} catch (SQLException e) {
				System.err.printf("Service(DataBase).load: SQLException - %s\n",
						e.getLocalizedMessage());
				return false;
			} catch (Exception e) {
				System.err.printf("Service(DataBase).load: Exception - %s\n",
						e.getLocalizedMessage());
				return false;
			}
		}
		
		return true;
	}
	
	public void save() {}
	
	public boolean start() {
		return connection != null;
	}
	
	public void stop() {
		try {
			connection.close();
		} catch (SQLException e) {
			System.err.printf("Service(DataBase).stop: SQLException - %s\n",
					e.getLocalizedMessage());
		}
	}
	
	public void run() {}

	public Connection getSqlConnection() {
		return connection;
	}
	
	public int count(String sql) {
		ResultSet rs = executeSqlQuery(sql);
		int count = 0;
		try {
			while(rs.next()) {
				count++;
			}
		} catch (SQLException e) {
			System.err.printf("Service(DataBase).count: SQLException - %s\n",
					e.getLocalizedMessage());
		}
		return count;
	}
	
	public boolean tableExists(String table) {
		try {
			DatabaseMetaData  dbmd = connection.getMetaData();
			ResultSet rs = dbmd.getTables(null, null, table, null);
			int count = 0;
			while(rs.next()) {
				count++;
			}
			return count > 0;
		} catch (SQLException e) {
			System.err.printf("Service(DataBase).tableExists: SQLException - %s\n",
					e.getLocalizedMessage());
			return false;
		}
	}
	
	public boolean executeSql(String sql) {
		try {
			Statement stmt = connection.createStatement();
			stmt.execute(sql);
		} catch (SQLException e) {
			System.err.printf("Service(DataBase).executeSql: SQLException - %s\n",
					e.getLocalizedMessage());
			return false;
		}
		return true;
	}
	
	public ResultSet executeSqlQuery(String sql) {
		try {
			Statement stmt = connection.createStatement();
			return stmt.executeQuery(sql);
		} catch (SQLException e) {
			System.err.printf("Service(DataBase).executeSql: SQLException - %s\n",
					e.getLocalizedMessage());
			return null;
		}
	}
	
	public PreparedStatement createPreparedStatement(String sql) {
		try {
			return connection.prepareStatement(sql);
		} catch (SQLException e) {
			System.err.printf("Service(DataBase).createPreparedStatement: SQLException - %s\n",
					e.getLocalizedMessage());
			return null;
		}
	}
	
	public boolean executeUpdate(PreparedStatement pstmt) {
		try {
			pstmt.executeUpdate();
		} catch (SQLException e) {
			System.err.printf("Service(DataBase).executeUpdate: SQLException - %s\n",
					e.getLocalizedMessage());
			return false;
		}
		return true;
	}
}
