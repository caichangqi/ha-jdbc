/*
 * HA-JDBC: High-Availability JDBC
 * Copyright (c) 2004-2009 Paul Ferraro
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

import java.sql.SQLException;

/**
 * @author paul
 *
 */
public class SimpleDatabaseClusterConfigurationFactory implements DatabaseClusterConfigurationFactory
{
	private final DatabaseClusterConfiguration<?, ?> configuration;
	
	public SimpleDatabaseClusterConfigurationFactory(DatabaseClusterConfiguration<?, ?> configuration)
	{
		this.configuration = configuration;
	}
	
	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfigurationFactory#createConfiguration(java.lang.String, java.lang.Class)
	 */
	@Override
	public <Z, D extends Database<Z>, C extends DatabaseClusterConfiguration<Z, D>> C createConfiguration(Class<C> targetClass) throws SQLException
	{
		return targetClass.cast(this.configuration);
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfigurationListener#added(net.sf.hajdbc.Database, net.sf.hajdbc.DatabaseClusterConfiguration)
	 */
	@Override
	public <Z, D extends Database<Z>> void added(D database, DatabaseClusterConfiguration<Z, D> configuration)
	{
	}

	/**
	 * {@inheritDoc}
	 * @see net.sf.hajdbc.DatabaseClusterConfigurationListener#removed(net.sf.hajdbc.Database, net.sf.hajdbc.DatabaseClusterConfiguration)
	 */
	@Override
	public <Z, D extends Database<Z>> void removed(D database, DatabaseClusterConfiguration<Z, D> configuration)
	{
	}
}