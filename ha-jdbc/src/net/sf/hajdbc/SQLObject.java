/*
 * HA-JDBC: High-Availability JDBC
 * Copyright (C) 2004 Paul Ferraro
 * 
 * This library is free software; you can redistribute it and/or modify it 
 * under the terms of the GNU Lesser General Public License as published by the 
 * Free Software Foundation; either version 2.1 of the License, or (at your 
 * option) any later version.
 * 
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or 
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License 
 * for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation, 
 * Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 * 
 * Contact: ferraro@users.sourceforge.net
 */
package net.sf.hajdbc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * @author  Paul Ferraro
 * @since   1.0
 */
public class SQLObject
{
	private static Log log = LogFactory.getLog(SQLObject.class);
	
	protected SQLObject parent;
	private DatabaseCluster databaseCluster;
	private Operation parentOperation;
	private Map objectMap;
	private List operationList = new LinkedList();
	
	protected SQLObject(SQLObject parent, Operation operation) throws java.sql.SQLException
	{
		this(parent.getDatabaseCluster(), parent.executeWriteToDatabase(operation));
		
		this.parent = parent;
		this.parentOperation = operation;
	}
	
	protected SQLObject(DatabaseCluster databaseCluster, Map objectMap)
	{
		this.databaseCluster = databaseCluster;
		this.objectMap = objectMap;
	}
	
	/**
	 * Returns the underlying SQL object for the specified database.
	 * If the sql object does not exist (this might be the case if the database was newly activated), it will be created from the stored operation.
	 * Any recorded operations are also executed. If the object could not be created, or if any of the executed operations failed, then the specified database is deactivated.
	 * @param database a database descriptor.
	 * @return an underlying SQL object
	 */
	public synchronized final Object getObject(Database database)
	{
		Object object = this.objectMap.get(database);
		
		if (object == null)
		{
			try
			{
				Object parentObject = this.parent.getObject(database);
				
				if (parentObject == null)
				{
					throw new java.sql.SQLException();
				}
				
				object = this.parentOperation.execute(database, parentObject);
				
				Iterator operations = this.operationList.iterator();
				
				while (operations.hasNext())
				{
					Operation operation = (Operation) operations.next();
					
					operation.execute(database, object);
				}
				
				this.objectMap.put(database, object);
			}
			catch (java.sql.SQLException e)
			{
				log.warn(Messages.getMessage(Messages.SQL_OBJECT_INIT_FAILED, new Object[] { this.getClass().getName(), database }), e);
				
				this.databaseCluster.deactivate(database);
			}
		}
		
		return object;
	}
	
	/**
	 * Records an operation.
	 * @param operation a database operation
	 */
	protected synchronized final void record(Operation operation)
	{
		this.operationList.add(operation);
	}
	
	/**
	 * Helper method that extracts the first result from a map of results.
	 * @param valueMap a Map<Database, Object> of operation execution results.
	 * @return a operation execution result
	 */
	protected final Object firstValue(Map valueMap)
	{
		return valueMap.values().iterator().next();
	}

	/**
	 * Executes the specified read operation on a single database in the cluster.
	 * It is assumed that these types of operation will <em>not</em> require access to the database.
	 * @param operation a database operation
	 * @return the result of the operation
	 * @throws java.sql.SQLException if operation execution fails
	 */
	public final Object executeReadFromDriver(Operation operation) throws java.sql.SQLException
	{
		try
		{
			Database database = this.databaseCluster.getBalancer().first();
			Object object = this.getObject(database);
			
			return operation.execute(database, object);
		}
		catch (NoSuchElementException e)
		{
			throw new SQLException(Messages.getMessage(Messages.NO_ACTIVE_DATABASES, this.databaseCluster));
		}
	}

	/**
	 * Executes the specified read operation on a single database in the cluster.
	 * It is assumed that these types of operation will require access to the database.
	 * @param operation a database operation
	 * @return the result of the operation
	 * @throws java.sql.SQLException if operation execution fails
	 */
	public final Object executeReadFromDatabase(Operation operation) throws java.sql.SQLException
	{
		Balancer balancer = this.databaseCluster.getBalancer();
		
		try
		{
			while (true)
			{
				Database database = balancer.next();
				Object object = this.getObject(database);
	
				try
				{
					balancer.beforeOperation(database);
					
					return operation.execute(database, object);
				}
				catch (Throwable e)
				{
					this.databaseCluster.handleFailure(database, e);
				}
				finally
				{
					balancer.afterOperation(database);
				}
			}
		}
		catch (NoSuchElementException e)
		{
			throw new SQLException(Messages.getMessage(Messages.NO_ACTIVE_DATABASES, this.databaseCluster));
		}
	}

	/**
	 * Executes the specified write operation on every database in the cluster in parallel.
	 * It is assumed that these types of operation will require access to the database.
	 * @param operation a database operation
	 * @return the result of the operation
	 * @throws java.sql.SQLException if operation execution fails
	 */
	public final Map executeWriteToDatabase(Operation operation) throws java.sql.SQLException
	{
		Database[] databases = this.getDatabases();
		Thread[] threads = new Thread[databases.length];
		
		Map returnValueMap = new HashMap(databases.length);
		Map exceptionMap = new HashMap(databases.length);

		for (int i = 0; i < databases.length; ++i)
		{
			Database database = databases[i];
			Object object = this.getObject(database);
			
			threads[i] = new Thread(new OperationExecutor(this.databaseCluster, operation, database, object, returnValueMap, exceptionMap));
			threads[i].start();
		}
		
		// Wait until all threads have completed
		for (int i = 0; i < threads.length; ++i)
		{
			Thread thread = threads[i];
			
			if ((thread != null) && thread.isAlive())
			{
				try
				{
					thread.join();
				}
				catch (InterruptedException e)
				{
					// Ignore
				}
			}
		}
		
		this.deactivateNewDatabases(databases);
		
		// If no databases returned successfully, return an exception back to the caller
		if (returnValueMap.isEmpty())
		{
			if (exceptionMap.isEmpty())
			{
				throw new SQLException(Messages.getMessage(Messages.NO_ACTIVE_DATABASES, this.databaseCluster));
			}
			
			throw new SQLException((Throwable) exceptionMap.get(databases[0]));
		}
		
		// If any databases failed, while others succeeded, deactivate them
		if (!exceptionMap.isEmpty())
		{
			this.handleExceptions(exceptionMap);
		}
		
		// Return results from successful operations
		return returnValueMap;
	}

	/**
	 * Executes the specified write operation on every database in the cluster.
	 * It is assumed that these types of operation will <em>not</em> require access to the database.
	 * @param operation a database operation
	 * @return the result of the operation
	 * @throws java.sql.SQLException if operation execution fails
	 */
	public final Map executeWriteToDriver(Operation operation) throws java.sql.SQLException
	{
		Database[] databases = this.databaseCluster.getBalancer().toArray();
		
		if (databases.length == 0)
		{
			throw new SQLException(Messages.getMessage(Messages.NO_ACTIVE_DATABASES, this.databaseCluster));
		}
		
		Map returnValueMap = new HashMap(databases.length);

		for (int i = 0; i < databases.length; ++i)
		{
			Database database = databases[i];
			Object object = this.getObject(database);
			
			returnValueMap.put(database, operation.execute(database, object));
		}
		
		this.deactivateNewDatabases(databases);
		
		this.record(operation);
		
		return returnValueMap;
	}
	
	/**
	 * Returns the database cluster to which this proxy is associated.
	 * @return a database cluster
	 */
	public DatabaseCluster getDatabaseCluster()
	{
		return this.databaseCluster;
	}
	
	/**
	 * @param exceptionMap
	 * @throws java.sql.SQLException
	 */
	public void handleExceptions(Map exceptionMap) throws java.sql.SQLException
	{
		Iterator exceptionMapEntries = exceptionMap.entrySet().iterator();
		
		while (exceptionMapEntries.hasNext())
		{
			Map.Entry exceptionMapEntry = (Map.Entry) exceptionMapEntries.next();
			Database database = (Database) exceptionMapEntry.getKey();
			Throwable exception = (Throwable) exceptionMapEntry.getValue();
			
			if (this.databaseCluster.deactivate(database))
			{
				log.error(Messages.getMessage(Messages.DATABASE_DEACTIVATED, new Object[] { database, this.databaseCluster }), exception);
			}
		}
	}

	private void deactivateNewDatabases(Database[] databases)
	{
		Set databaseSet = new HashSet(Arrays.asList(this.databaseCluster.getBalancer().toArray()));
		
		for (int i = 0; i < databases.length; ++i)
		{
			databaseSet.remove(databases[i]);
		}
		
		if (!databaseSet.isEmpty())
		{
			Iterator newDatabases = databaseSet.iterator();
			
			while (newDatabases.hasNext())
			{
				Database newDatabase = (Database) newDatabases.next();
				
				this.databaseCluster.deactivate(newDatabase);
			}
		}
	}
	
	private Database[] getDatabases() throws SQLException
	{
		Database[] databases = this.databaseCluster.getBalancer().toArray();
		
		if (databases.length == 0)
		{
			throw new SQLException(Messages.getMessage(Messages.NO_ACTIVE_DATABASES, this.databaseCluster));
		}
		
		return databases;
	}
}