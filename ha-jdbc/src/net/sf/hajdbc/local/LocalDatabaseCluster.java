/*
 * Copyright (c) 2004, Identity Theft 911, LLC.  All rights reserved.
 */
package net.sf.hajdbc.local;

import java.sql.Connection;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.sf.hajdbc.Database;
import net.sf.hajdbc.DatabaseCluster;
import net.sf.hajdbc.DatabaseClusterDescriptor;
import net.sf.hajdbc.DatabaseConnector;
import net.sf.hajdbc.SQLException;

/**
 * @author  Paul Ferraro
 * @version $Revision$
 * @since   1.0
 */
public class LocalDatabaseCluster extends DatabaseCluster
{
	private Set activeDatabaseSet = new LinkedHashSet();
	private LocalDatabaseClusterDescriptor descriptor;
	private DatabaseConnector databaseConnector;
	
	/**
	 * Constructs a new DatabaseCluster.
	 * @param descriptor
	 * @param databaseMap
	 */
	public LocalDatabaseCluster(LocalDatabaseClusterDescriptor descriptor) throws java.sql.SQLException
	{
		this.descriptor = descriptor;
		
		Map databaseMap = descriptor.getDatabaseMap();
		Map databaseConnectorMap = new HashMap(databaseMap.size());
		
		Iterator databases = databaseMap.values().iterator();
		
		while (databases.hasNext())
		{
			Database database = (Database) databases.next();
			
			databaseConnectorMap.put(database, database.getDatabaseConnector());
		}
		
		this.databaseConnector = new DatabaseConnector(this, Collections.synchronizedMap(databaseConnectorMap));
		
		databases = databaseMap.values().iterator();
		
		while (databases.hasNext())
		{
			Database database = (Database) databases.next();
			
			if (this.isActive(database))
			{
				this.activeDatabaseSet.add(database);
			}
		}
	}

	public DatabaseConnector getDatabaseConnector()
	{
		return this.databaseConnector;
	}
	
	/**
	 * @param database
	 * @return true if the specified database is active, false otherwise
	 */
	public boolean isActive(Database database)
	{
		Connection connection = null;
		
		Object databaseConnector = this.databaseConnector.getSQLObject(database);
		
		try
		{
			connection = database.connect(databaseConnector);
			
			Statement statement = connection.createStatement();
			
			statement.execute(this.descriptor.getValidateSQL());

			statement.close();
			
			return true;
		}
		catch (java.sql.SQLException e)
		{
			return false;
		}
		finally
		{
			if (connection != null)
			{
				try
				{
					connection.close();
				}
				catch (java.sql.SQLException e)
				{
					// Ignore
				}
			}
		}
	}
	
	/**
	 * Deactivates the specified database.
	 * @param database
	 * @return true if the database was successfully deactivated, false if it was already deactivated
	 */
	public boolean deactivate(Database database)
	{
		synchronized (this.activeDatabaseSet)
		{
			return this.activeDatabaseSet.remove(database);
		}
	}

	/**
	 * @see net.sf.hajdbc.DatabaseClusterMBean#getName()
	 */
	public String getName()
	{
		return this.descriptor.getName();
	}

	public DatabaseClusterDescriptor getDescriptor()
	{
		return this.descriptor;
	}
	
	public boolean activate(Database database)
	{
		synchronized (this.activeDatabaseSet)
		{
			return this.activeDatabaseSet.add(database);
		}
	}
	
	/**
	 * Returns the first database in the cluster
	 * @return the first database in the cluster
	 * @throws SQLException
	 */
	public Database firstDatabase() throws SQLException
	{
		synchronized (this.activeDatabaseSet)
		{
			if (this.activeDatabaseSet.size() == 0)
			{
				throw new SQLException("No active databases in cluster");
			}
			
			return (Database) this.activeDatabaseSet.iterator().next();
		}
	}
	
	/**
	 * Returns the next database in the cluster
	 * @return the next database in the cluster
	 * @throws SQLException
	 */
	public Database nextDatabase() throws SQLException
	{
		synchronized (this.activeDatabaseSet)
		{
			Database database = this.firstDatabase();
			
			if (this.activeDatabaseSet.size() > 1)
			{
				this.activeDatabaseSet.remove(database);
				
				this.activeDatabaseSet.add(database);
			}
			
			return database;
		}
	}

	/**
	 * A list of active databases in this cluster
	 * @return a list of Database objects
	 * @throws SQLException
	 */
	public List getActiveDatabaseList() throws SQLException
	{
		synchronized (this.activeDatabaseSet)
		{
			if (this.activeDatabaseSet.size() == 0)
			{
				throw new SQLException("No active databases in cluster");
			}
			
			return new ArrayList(this.activeDatabaseSet);
		}
	}

	public String[] getActiveDatabases() throws SQLException
	{
		List databaseList = this.getActiveDatabaseList();
		String[] databases = new String[databaseList.size()];
		
		for (int i = 0; i < databaseList.size(); ++i)
		{
			databases[i] = ((Database) databaseList.get(i)).getId();
		}
		
		return databases;
	}
	/**
	 * @see net.sf.hajdbc.DatabaseCluster#getDatabase(java.lang.String)
	 */
	protected Database getDatabase(String databaseId)
	{
		return (Database) this.descriptor.getDatabaseMap().get(databaseId);
	}
}
