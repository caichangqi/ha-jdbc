/*
 * HA-JDBC: High-Availability JDBC
 * Copyright (c) 2004-2007 Paul Ferraro
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
package net.sf.hajdbc.balancer.simple;

import java.util.Collections;
import java.util.Comparator;
import java.util.Set;

import net.sf.hajdbc.Database;
import net.sf.hajdbc.balancer.AbstractSetBalancer;

/**
 * Trivial balancer implementation whose {@link #next} implementation always returns the database with the highest weight.
 * 
 * @author  Paul Ferraro
 * @param <D> either java.sql.Driver or javax.sql.DataSource
 */
public class SimpleBalancer<Z, D extends Database<Z>> extends AbstractSetBalancer<Z, D>
{
	private volatile D nextDatabase = null;
	
	private Comparator<D> comparator = new Comparator<D>()
	{
		@Override
		public int compare(D database1, D database2)
		{
			return database1.getWeight() - database2.getWeight();
		}
	};

	/**
	 * @see net.sf.hajdbc.balancer.Balancer#next()
	 */
	@Override
	public D next()
	{
		return this.nextDatabase;
	}

	/**
	 * @see net.sf.hajdbc.balancer.AbstractBalancer#added(net.sf.hajdbc.Database)
	 */
	@Override
	protected void added(D database)
	{
		this.reset();
	}

	/**
	 * @see net.sf.hajdbc.balancer.AbstractBalancer#removed(net.sf.hajdbc.Database)
	 */
	@Override
	protected void removed(D database)
	{
		this.reset();
	}
	
	private void reset()
	{
		Set<D> databaseSet = this.getDatabaseSet();
		
		this.nextDatabase = databaseSet.isEmpty() ? null : Collections.max(databaseSet, this.comparator);
	}

	/**
	 * @see net.sf.hajdbc.balancer.AbstractBalancer#cleared()
	 */
	@Override
	protected void cleared()
	{
		this.nextDatabase = null;
	}
}
