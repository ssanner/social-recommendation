package org.nicta.lr.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SQLUtil 
{
	private static Connection connection = null;
	
	/**
	 * Returns the SQL connection Singleton
	 * 
	 * @return
	 * @throws SQLException
	 */
	public static Connection getSqlConnection()
		throws SQLException
	{
		try {
			Class.forName ("com.mysql.jdbc.Driver");
		}
		catch (ClassNotFoundException ce) {
			System.out.println("MySQL driver not found");
			System.exit(1);
		}
		
		if (connection == null) {
			connection = DriverManager.getConnection(Constants.DB_STRING);
			connection.setAutoCommit(false);
		}
	
		return connection;
	}
	
	/**
	 * Cleans up and commits the updates to the database
	 * 
	 * @throws SQLException
	 */
	public static void closeSqlConnection()
		throws SQLException
	{
		if (connection != null) {
			connection.commit();
			connection.close();
			connection = null;
		}
		else {
			System.out.println("Connection died before it could be commited. FAIL");
		}
	}
}
