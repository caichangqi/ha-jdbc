/*
 * HA-JDBC: High-Availability JDBC
 * Copyright (c) 2004-2006 Paul Ferraro
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
package net.sf.hajdbc.sync;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.TreeMap;

import net.sf.hajdbc.ColumnProperties;
import net.sf.hajdbc.DatabaseMetaDataCache;
import net.sf.hajdbc.DatabaseProperties;
import net.sf.hajdbc.Dialect;
import net.sf.hajdbc.ForeignKeyConstraint;
import net.sf.hajdbc.SynchronizationStrategy;
import net.sf.hajdbc.TableProperties;
import net.sf.hajdbc.UniqueConstraint;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

/**
 * @author Paul Ferraro
 *
 */
public class TestDifferentialSynchronizationStrategy implements SynchronizationStrategy
{
	private IMocksControl control = EasyMock.createStrictControl();
	
	private SynchronizationStrategy strategy = new DifferentialSynchronizationStrategy();
	
	/**
	 * @see net.sf.hajdbc.SynchronizationStrategy#requiresTableLocking()
	 */
	@Test
	public boolean requiresTableLocking()
	{
		boolean requiresTableLocking = this.strategy.requiresTableLocking();
		
		assert requiresTableLocking;
		
		return requiresTableLocking;
	}

	@DataProvider(name = "sync")
	public Object[][] syncProvider()
	{
		return new Object[][] { new Object[] { this.control.createMock(Connection.class), this.control.createMock(Connection.class), this.control.createMock(DatabaseMetaDataCache.class), this.control.createMock(Dialect.class) } };
	}
	
	/**
	 * @see net.sf.hajdbc.SynchronizationStrategy#synchronize(java.sql.Connection, java.sql.Connection, net.sf.hajdbc.DatabaseMetaDataCache, net.sf.hajdbc.Dialect)
	 */
	@Test(dataProvider = "sync")
	public void synchronize(Connection inactiveConnection, Connection activeConnection, DatabaseMetaDataCache metaData, Dialect dialect) throws SQLException
	{
		Statement statement = this.control.createMock(Statement.class);
		DatabaseProperties databaseProperties = this.control.createMock(DatabaseProperties.class);
		TableProperties tableProperties = this.control.createMock(TableProperties.class);
		ForeignKeyConstraint foreignKey = this.control.createMock(ForeignKeyConstraint.class);
		UniqueConstraint primaryKey = this.control.createMock(UniqueConstraint.class);
		UniqueConstraint uniqueKey = this.control.createMock(UniqueConstraint.class);
		Statement inactiveStatement = this.control.createMock(Statement.class);
		ResultSet inactiveResultSet = this.control.createMock(ResultSet.class);
		Statement activeStatement = this.control.createMock(Statement.class);
		ResultSet activeResultSet = this.control.createMock(ResultSet.class);
		PreparedStatement deleteStatement = this.control.createMock(PreparedStatement.class);
		PreparedStatement insertStatement = this.control.createMock(PreparedStatement.class);
		PreparedStatement updateStatement = this.control.createMock(PreparedStatement.class);
		ColumnProperties column1Properties = this.control.createMock(ColumnProperties.class);
		ColumnProperties column2Properties = this.control.createMock(ColumnProperties.class);
		ColumnProperties column3Properties = this.control.createMock(ColumnProperties.class);
		ColumnProperties column4Properties = this.control.createMock(ColumnProperties.class);
		
		inactiveConnection.setAutoCommit(true);
		
		EasyMock.expect(inactiveConnection.createStatement()).andReturn(statement);
		
		EasyMock.expect(metaData.getDatabaseProperties(inactiveConnection)).andReturn(databaseProperties);
		EasyMock.expect(databaseProperties.getTables()).andReturn(Collections.singleton(tableProperties));
		EasyMock.expect(tableProperties.getForeignKeyConstraints()).andReturn(Collections.singleton(foreignKey));
		EasyMock.expect(dialect.getDropForeignKeyConstraintSQL(foreignKey)).andReturn("drop fk");
		
		statement.addBatch("drop fk");
		EasyMock.expect(statement.executeBatch()).andReturn(null);
		statement.clearBatch();
		
		inactiveConnection.setAutoCommit(false);
		
		EasyMock.expect(tableProperties.getPrimaryKey()).andReturn(primaryKey);
		EasyMock.expect(primaryKey.getColumnList()).andReturn(Arrays.asList(new String[] { "column1", "column2" }));
		EasyMock.expect(tableProperties.getUniqueConstraints()).andReturn(new ArrayList<UniqueConstraint>(Arrays.asList(new UniqueConstraint[] { primaryKey, uniqueKey })));
		EasyMock.expect(dialect.getDropUniqueConstraintSQL(uniqueKey)).andReturn("drop uk");
		
		statement.addBatch("drop uk");
		EasyMock.expect(statement.executeBatch()).andReturn(null);
		statement.clearBatch();

		EasyMock.expect(tableProperties.getColumns()).andReturn(Arrays.asList(new String[] { "column1", "column2", "column3", "column4" }));
		EasyMock.expect(tableProperties.getName()).andReturn("table");
		
		EasyMock.expect(inactiveConnection.createStatement()).andReturn(inactiveStatement);
		inactiveStatement.setFetchSize(0);

		// Disable order checking, since statement is executed asynchronously
		this.control.checkOrder(false);
		
		EasyMock.expect(inactiveStatement.executeQuery("SELECT column1, column2, column3, column4 FROM table ORDER BY column1, column2")).andReturn(inactiveResultSet);

		EasyMock.expect(activeConnection.createStatement()).andReturn(activeStatement);
		
		activeStatement.setFetchSize(0);
		
		EasyMock.expect(activeStatement.executeQuery("SELECT column1, column2, column3, column4 FROM table ORDER BY column1, column2")).andReturn(activeResultSet);

		this.control.checkOrder(true);
		
		EasyMock.expect(tableProperties.getName()).andReturn("table");
		EasyMock.expect(inactiveConnection.prepareStatement("DELETE FROM table WHERE column1 = ? AND column2 = ?")).andReturn(deleteStatement);
		EasyMock.expect(tableProperties.getName()).andReturn("table");
		EasyMock.expect(inactiveConnection.prepareStatement("INSERT INTO table (column1, column2, column3, column4) VALUES (?, ?, ?, ?)")).andReturn(insertStatement);
		EasyMock.expect(tableProperties.getName()).andReturn("table");
		EasyMock.expect(inactiveConnection.prepareStatement("UPDATE table SET column3 = ?, column4 = ? WHERE column1 = ? AND column2 = ?")).andReturn(updateStatement);

		EasyMock.expect(activeResultSet.next()).andReturn(true);
		EasyMock.expect(inactiveResultSet.next()).andReturn(true);
		
		// Trigger insert
		EasyMock.expect(activeResultSet.getObject(1)).andReturn(1);
		EasyMock.expect(inactiveResultSet.getObject(1)).andReturn(1);
		EasyMock.expect(activeResultSet.getObject(2)).andReturn(1);
		EasyMock.expect(inactiveResultSet.getObject(2)).andReturn(2);
		
		insertStatement.clearParameters();
		
		EasyMock.expect(activeResultSet.getObject(1)).andReturn(1);
		EasyMock.expect(tableProperties.getColumn("column1")).andReturn(column1Properties);
		EasyMock.expect(dialect.getColumnType(column1Properties)).andReturn(Types.INTEGER);
		EasyMock.expect(activeResultSet.wasNull()).andReturn(false);
		insertStatement.setObject(1, 1, Types.INTEGER);
		
		EasyMock.expect(activeResultSet.getObject(2)).andReturn(1);
		EasyMock.expect(tableProperties.getColumn("column2")).andReturn(column2Properties);
		EasyMock.expect(dialect.getColumnType(column2Properties)).andReturn(Types.INTEGER);
		EasyMock.expect(activeResultSet.wasNull()).andReturn(false);
		insertStatement.setObject(2, 1, Types.INTEGER);

		EasyMock.expect(activeResultSet.getObject(3)).andReturn("");
		EasyMock.expect(tableProperties.getColumn("column3")).andReturn(column3Properties);
		EasyMock.expect(dialect.getColumnType(column3Properties)).andReturn(Types.VARCHAR);
		EasyMock.expect(activeResultSet.wasNull()).andReturn(false);
		insertStatement.setObject(3, "", Types.VARCHAR);
		
		EasyMock.expect(activeResultSet.getObject(4)).andReturn(null);
		EasyMock.expect(tableProperties.getColumn("column4")).andReturn(column4Properties);
		EasyMock.expect(dialect.getColumnType(column4Properties)).andReturn(Types.VARCHAR);
		EasyMock.expect(activeResultSet.wasNull()).andReturn(true);
		insertStatement.setNull(4, Types.VARCHAR);
		
		insertStatement.addBatch();
		
		EasyMock.expect(activeResultSet.next()).andReturn(true);
		
		// Trigger update
		EasyMock.expect(activeResultSet.getObject(1)).andReturn(1);
		EasyMock.expect(inactiveResultSet.getObject(1)).andReturn(1);
		EasyMock.expect(activeResultSet.getObject(2)).andReturn(2);
		EasyMock.expect(inactiveResultSet.getObject(2)).andReturn(2);

		updateStatement.clearParameters();
		
		// Nothing to update
		EasyMock.expect(activeResultSet.getObject(3)).andReturn("");
		EasyMock.expect(inactiveResultSet.getObject(3)).andReturn("");
		EasyMock.expect(tableProperties.getColumn("column3")).andReturn(column3Properties);
		EasyMock.expect(dialect.getColumnType(column3Properties)).andReturn(Types.VARCHAR);
		EasyMock.expect(activeResultSet.wasNull()).andReturn(false);
		updateStatement.setObject(1, "", Types.VARCHAR);
		EasyMock.expect(inactiveResultSet.wasNull()).andReturn(false);
		
		// Nothing to update
		EasyMock.expect(activeResultSet.getObject(4)).andReturn(null);
		EasyMock.expect(inactiveResultSet.getObject(4)).andReturn(null);
		EasyMock.expect(tableProperties.getColumn("column4")).andReturn(column4Properties);
		EasyMock.expect(dialect.getColumnType(column4Properties)).andReturn(Types.VARCHAR);
		EasyMock.expect(activeResultSet.wasNull()).andReturn(true);
		updateStatement.setNull(2, Types.VARCHAR);
		EasyMock.expect(inactiveResultSet.wasNull()).andReturn(true);
		
		EasyMock.expect(activeResultSet.next()).andReturn(true);
		EasyMock.expect(inactiveResultSet.next()).andReturn(true);
		
		// Trigger update
		EasyMock.expect(activeResultSet.getObject(1)).andReturn(1);
		EasyMock.expect(inactiveResultSet.getObject(1)).andReturn(1);
		EasyMock.expect(activeResultSet.getObject(2)).andReturn(3);
		EasyMock.expect(inactiveResultSet.getObject(2)).andReturn(3);

		updateStatement.clearParameters();

		EasyMock.expect(activeResultSet.getObject(3)).andReturn("");
		EasyMock.expect(inactiveResultSet.getObject(3)).andReturn(null);
		EasyMock.expect(tableProperties.getColumn("column3")).andReturn(column3Properties);
		EasyMock.expect(dialect.getColumnType(column3Properties)).andReturn(Types.VARCHAR);
		EasyMock.expect(activeResultSet.wasNull()).andReturn(false);
		updateStatement.setObject(1, "", Types.VARCHAR);
		EasyMock.expect(inactiveResultSet.wasNull()).andReturn(true);
		
		EasyMock.expect(activeResultSet.getObject(4)).andReturn(null);
		EasyMock.expect(inactiveResultSet.getObject(4)).andReturn("");
		EasyMock.expect(tableProperties.getColumn("column4")).andReturn(column4Properties);
		EasyMock.expect(dialect.getColumnType(column4Properties)).andReturn(Types.VARCHAR);
		EasyMock.expect(activeResultSet.wasNull()).andReturn(true);
		updateStatement.setNull(2, Types.VARCHAR);
		EasyMock.expect(inactiveResultSet.wasNull()).andReturn(false);
		
		EasyMock.expect(tableProperties.getColumn("column1")).andReturn(column1Properties);
		EasyMock.expect(dialect.getColumnType(column1Properties)).andReturn(Types.INTEGER);
		EasyMock.expect(inactiveResultSet.getObject(1)).andReturn(1);
		updateStatement.setObject(3, 1, Types.INTEGER);
		
		EasyMock.expect(tableProperties.getColumn("column2")).andReturn(column2Properties);
		EasyMock.expect(dialect.getColumnType(column2Properties)).andReturn(Types.INTEGER);
		EasyMock.expect(inactiveResultSet.getObject(2)).andReturn(3);
		updateStatement.setObject(4, 3, Types.INTEGER);

		updateStatement.addBatch();
		
		EasyMock.expect(activeResultSet.next()).andReturn(false);
		EasyMock.expect(inactiveResultSet.next()).andReturn(true);
		
		deleteStatement.clearParameters();

		EasyMock.expect(tableProperties.getColumn("column1")).andReturn(column1Properties);
		EasyMock.expect(dialect.getColumnType(column1Properties)).andReturn(Types.INTEGER);
		EasyMock.expect(inactiveResultSet.getObject(1)).andReturn(2);
		deleteStatement.setObject(1, 2, Types.INTEGER);
		
		EasyMock.expect(tableProperties.getColumn("column2")).andReturn(column2Properties);
		EasyMock.expect(dialect.getColumnType(column2Properties)).andReturn(Types.INTEGER);
		EasyMock.expect(inactiveResultSet.getObject(2)).andReturn(1);
		deleteStatement.setObject(2, 1, Types.INTEGER);
		
		deleteStatement.addBatch();
		
		EasyMock.expect(inactiveResultSet.next()).andReturn(false);
		
		EasyMock.expect(deleteStatement.executeBatch()).andReturn(null);
		deleteStatement.close();
		
		EasyMock.expect(insertStatement.executeBatch()).andReturn(null);
		insertStatement.close();
		
		EasyMock.expect(updateStatement.executeBatch()).andReturn(null);
		updateStatement.close();
		
		inactiveStatement.close();
		activeStatement.close();
		
		EasyMock.expect(dialect.getCreateUniqueConstraintSQL(uniqueKey)).andReturn("create uk");
		
		statement.addBatch("create uk");
		EasyMock.expect(statement.executeBatch()).andReturn(null);
		statement.clearBatch();

		inactiveConnection.commit();
		
		EasyMock.expect(tableProperties.getName()).andReturn("table").times(3);
		
		inactiveConnection.setAutoCommit(true);

		EasyMock.expect(tableProperties.getForeignKeyConstraints()).andReturn(Collections.singleton(foreignKey));
		EasyMock.expect(dialect.getCreateForeignKeyConstraintSQL(foreignKey)).andReturn("create fk");
		
		statement.addBatch("create fk");
		EasyMock.expect(statement.executeBatch()).andReturn(null);
		statement.clearBatch();
		
		TreeMap<String, Long> activeSequenceMap = new TreeMap<String, Long>();
		activeSequenceMap.put("sequence1", 1L);
		activeSequenceMap.put("sequence2", 2L);

		TreeMap<String, Long> inactiveSequenceMap = new TreeMap<String, Long>();
		inactiveSequenceMap.put("sequence1", 1L);
		inactiveSequenceMap.put("sequence2", 1L);
		
		EasyMock.expect(dialect.getSequences(activeConnection)).andReturn(activeSequenceMap);
		EasyMock.expect(dialect.getSequences(inactiveConnection)).andReturn(inactiveSequenceMap);
		EasyMock.expect(dialect.getAlterSequenceSQL("sequence2", 2L)).andReturn("alter sequence");
		
		statement.addBatch("alter sequence");
		EasyMock.expect(statement.executeBatch()).andReturn(null);
		statement.close();
		
		this.control.replay();
		
		this.strategy.synchronize(inactiveConnection, activeConnection, metaData, dialect);
		
		this.control.verify();
		this.control.reset();
	}
}