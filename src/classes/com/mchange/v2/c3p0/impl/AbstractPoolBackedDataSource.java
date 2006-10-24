/*
 * Distributed as part of c3p0 v.0.9.1-pre10
 *
 * Copyright (C) 2005 Machinery For Change, Inc.
 *
 * Author: Steve Waldman <swaldman@mchange.com>
 *
 * This library is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License version 2.1, as 
 * published by the Free Software Foundation.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this software; see the file LICENSE.  If not, write to the
 * Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 * Boston, MA 02111-1307, USA.
 */


package com.mchange.v2.c3p0.impl;

import java.io.*;
import java.sql.*;

import javax.sql.*;

import com.mchange.lang.ThrowableUtils;
import com.mchange.v2.c3p0.*;
import com.mchange.v2.log.*;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyVetoException;
import java.beans.VetoableChangeListener;
import java.beans.PropertyChangeListener;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;
import com.mchange.v2.c3p0.cfg.C3P0Config;

public abstract class AbstractPoolBackedDataSource extends PoolBackedDataSourceBase implements PooledDataSource
{
    final static MLogger logger = MLog.getLogger( AbstractPoolBackedDataSource.class );

    final static String NO_CPDS_ERR_MSG =
        "Attempted to use an uninitialized PoolBackedDataSource. " +
        "Please call setConnectionPoolDataSource( ... ) to initialize.";

    //MT: protected by this' lock
    transient C3P0PooledConnectionPoolManager poolManager;
    transient boolean is_closed = false;
    //MT: end protected by this' lock

    protected AbstractPoolBackedDataSource( boolean autoregister )
    {
        super( autoregister );
        setUpPropertyEvents();
    }

    protected AbstractPoolBackedDataSource(String configName)
    { 
        this( true );
        initializeNamedConfig( configName );
    }

    private void setUpPropertyEvents()
    {
        PropertyChangeListener l = new PropertyChangeListener()
        {
            public void propertyChange( PropertyChangeEvent evt )
            { resetPoolManager(); }
        };
        this.addPropertyChangeListener( l );
    }


    protected void initializeNamedConfig(String configName)
    {
        try
        {
            if (configName != null)
            {
                C3P0Config.bindNamedConfigToBean( this, configName ); 
                if ( this.getDataSourceName().equals( this.getIdentityToken() ) ) //dataSourceName has not been specified in config
                    this.setDataSourceName( configName );
            }
        }
        catch (Exception e)
        {
            if (logger.isLoggable( MLevel.WARNING ))
                logger.log( MLevel.WARNING, 
                        "Error binding PoolBackedDataSource to named-config '" + configName + 
                        "'. Some default-config values may be used.", 
                        e);
        }
    }

//  Commented out method is just super.getReference() with a lot of extra printing

//  public javax.naming.Reference getReference() throws javax.naming.NamingException
//  {
//  System.err.println("getReference()!!!!");
//  new Exception("PRINT-STACK-TRACE").printStackTrace();
//  javax.naming.Reference out = super.getReference();
//  System.err.println(out);
//  return out;
//  }

    // report our ID token as dataSourceName if we have no
    // name explicitly set
    public String getDataSourceName()
    {
 	String out = super.getDataSourceName();
 	if (out == null)
 	    out = this.getIdentityToken();
 	return out;
    }

    //implementation of javax.sql.DataSource
    public Connection getConnection() throws SQLException
    {
        PooledConnection pc = getPoolManager().getPool().checkoutPooledConnection();
        return pc.getConnection();
    }

    public Connection getConnection(String username, String password) throws SQLException
    { 
        PooledConnection pc = getPoolManager().getPool(username, password).checkoutPooledConnection();
        return pc.getConnection();
    }

    public PrintWriter getLogWriter() throws SQLException
    { return assertCpds().getLogWriter(); }

    public void setLogWriter(PrintWriter out) throws SQLException
    { assertCpds().setLogWriter( out ); }

    public int getLoginTimeout() throws SQLException
    { return assertCpds().getLoginTimeout(); }

    public void setLoginTimeout(int seconds) throws SQLException
    { assertCpds().setLoginTimeout( seconds ); }

    //implementation of com.mchange.v2.c3p0.PoolingDataSource
    public int getNumConnections() throws SQLException
    { return getPoolManager().getPool().getNumConnections(); }

    public int getNumIdleConnections() throws SQLException
    { return getPoolManager().getPool().getNumIdleConnections(); }

    public int getNumBusyConnections() throws SQLException
    { return getPoolManager().getPool().getNumBusyConnections(); }

    public int getNumUnclosedOrphanedConnections() throws SQLException
    { return getPoolManager().getPool().getNumUnclosedOrphanedConnections(); }

    public int getNumConnectionsDefaultUser() throws SQLException
    { return getNumConnections();}

    public int getNumIdleConnectionsDefaultUser() throws SQLException
    { return getNumIdleConnections();}

    public int getNumBusyConnectionsDefaultUser() throws SQLException
    { return getNumBusyConnections();}

    public int getNumUnclosedOrphanedConnectionsDefaultUser() throws SQLException
    { return getNumUnclosedOrphanedConnections();}

    public int getStatementCacheNumStatementsDefaultUser() throws SQLException
    { return getPoolManager().getPool().getStatementCacheNumStatements(); }

    public int getStatementCacheNumCheckedOutDefaultUser() throws SQLException
    { return getPoolManager().getPool().getStatementCacheNumCheckedOut(); }

    public int getStatementCacheNumConnectionsWithCachedStatementsDefaultUser() throws SQLException
    { return getPoolManager().getPool().getStatementCacheNumConnectionsWithCachedStatements(); }

    public float getEffectivePropertyCycleDefaultUser() throws SQLException
    { return getPoolManager().getPool().getEffectivePropertyCycle(); }
    
    public long getStartTimeMillisDefaultUser() throws SQLException
    { return getPoolManager().getPool().getStartTime(); }

    public long getUpTimeMillisDefaultUser() throws SQLException
    { return getPoolManager().getPool().getUpTime(); }
    
    public long getNumFailedCheckinsDefaultUser() throws SQLException
    { return getPoolManager().getPool().getNumFailedCheckins(); }

    public long getNumFailedCheckoutsDefaultUser() throws SQLException
    { return getPoolManager().getPool().getNumFailedCheckouts(); }

    public long getNumFailedIdleTestsDefaultUser() throws SQLException
    { return getPoolManager().getPool().getNumFailedIdleTests(); }

    public int getThreadPoolSize() throws SQLException
    { return getPoolManager().getThreadPoolSize(); }

    public int getThreadPoolNumActiveThreads() throws SQLException
    { return getPoolManager().getThreadPoolNumActiveThreads(); }

    public int getThreadPoolNumIdleThreads() throws SQLException
    { return getPoolManager().getThreadPoolNumIdleThreads(); }

    public int getThreadPoolNumTasksPending() throws SQLException
    { return getPoolManager().getThreadPoolNumTasksPending(); }

    public String sampleThreadPoolStackTraces() throws SQLException
    { return getPoolManager().getThreadPoolStackTraces(); }

    public String sampleThreadPoolStatus() throws SQLException
    { return getPoolManager().getThreadPoolStatus(); }

    public String sampleStatementCacheStatusDefaultUser() throws SQLException
    { return getPoolManager().getPool().dumpStatementCacheStatus(); }
    
    public String sampleStatementCacheStatus(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).dumpStatementCacheStatus(); }
    
    public Throwable getLastCheckinFailureDefaultUser() throws SQLException
    { return getPoolManager().getPool().getLastCheckinFailure(); }

    public Throwable getLastCheckoutFailureDefaultUser() throws SQLException
    { return getPoolManager().getPool().getLastCheckoutFailure(); }

    public Throwable getLastIdleTestFailureDefaultUser() throws SQLException
    { return getPoolManager().getPool().getLastIdleTestFailure(); }

    public Throwable getLastConnectionTestFailureDefaultUser() throws SQLException
    { return getPoolManager().getPool().getLastConnectionTestFailure(); }
    
    public Throwable getLastCheckinFailure(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getLastCheckinFailure(); }

    public Throwable getLastCheckoutFailure(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getLastCheckoutFailure(); }

    public Throwable getLastIdleTestFailure(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getLastIdleTestFailure(); }

    public Throwable getLastConnectionTestFailure(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getLastConnectionTestFailure(); }
    
    public String sampleLastCheckinFailureStackTraceDefaultUser() throws SQLException
    { 
        Throwable t = getLastCheckinFailureDefaultUser(); 
        return t == null ? null : ThrowableUtils.extractStackTrace( t ); 
    }
    
    public String sampleLastCheckoutFailureStackTraceDefaultUser() throws SQLException
    { 
        Throwable t = getLastCheckoutFailureDefaultUser(); 
        return t == null ? null : ThrowableUtils.extractStackTrace( t ); 
    }

    public String sampleLastIdleTestFailureStackTraceDefaultUser() throws SQLException
    { 
        Throwable t = getLastIdleTestFailureDefaultUser(); 
        return t == null ? null : ThrowableUtils.extractStackTrace( t ); 
    }

    public String sampleLastConnectionTestFailureStackTraceDefaultUser() throws SQLException
    { 
        Throwable t = getLastConnectionTestFailureDefaultUser();
        return t == null ? null : ThrowableUtils.extractStackTrace( t ); 
    }
    
    public String sampleLastCheckinFailureStackTrace(String username, String password) throws SQLException
    { 
        Throwable t = getLastCheckinFailure(username, password); 
        return t == null ? null : ThrowableUtils.extractStackTrace( t ); 
    }

    public String sampleLastCheckoutFailureStackTrace(String username, String password) throws SQLException
    { 
        Throwable t = getLastCheckoutFailure(username, password); 
        return t == null ? null : ThrowableUtils.extractStackTrace( t ); 
    }

    public String sampleLastIdleTestFailureStackTrace(String username, String password) throws SQLException
    { 
        Throwable t = getLastIdleTestFailure(username, password); 
        return t == null ? null : ThrowableUtils.extractStackTrace( t ); 
    }

    public String sampleLastConnectionTestFailureStackTrace(String username, String password) throws SQLException
    { 
        Throwable t = getLastConnectionTestFailure(username, password); 
        return t == null ? null : ThrowableUtils.extractStackTrace( t ); 
    }

    public void softResetDefaultUser() throws SQLException
    { getPoolManager().getPool().reset(); }

    public int getNumConnections(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getNumConnections(); }

    public int getNumIdleConnections(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getNumIdleConnections(); }

    public int getNumBusyConnections(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getNumBusyConnections(); }

    public int getNumUnclosedOrphanedConnections(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getNumUnclosedOrphanedConnections(); }

    public int getStatementCacheNumStatements(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getStatementCacheNumStatements(); }

    public int getStatementCacheNumCheckedOut(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getStatementCacheNumCheckedOut(); }

    public int getStatementCacheNumConnectionsWithCachedStatements(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getStatementCacheNumConnectionsWithCachedStatements(); }

    public float getEffectivePropertyCycle(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getEffectivePropertyCycle(); }

    public long getStartTimeMillis(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getStartTime(); }

    public long getUpTimeMillis(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getUpTime(); }
    
    public long getNumFailedCheckins(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getNumFailedCheckins(); }

    public long getNumFailedCheckouts(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getNumFailedCheckouts(); }

    public long getNumFailedIdleTests(String username, String password) throws SQLException
    { return getPoolManager().getPool(username, password).getNumFailedIdleTests(); }

    public void softReset(String username, String password) throws SQLException
    { getPoolManager().getPool(username, password).reset(); }

    public int getNumBusyConnectionsAllUsers() throws SQLException
    { return getPoolManager().getNumBusyConnectionsAllAuths(); }

    public int getNumIdleConnectionsAllUsers() throws SQLException
    { return getPoolManager().getNumIdleConnectionsAllAuths(); }

    public int getNumConnectionsAllUsers() throws SQLException
    { return getPoolManager().getNumConnectionsAllAuths(); }

    public int getNumUnclosedOrphanedConnectionsAllUsers() throws SQLException
    { return getPoolManager().getNumUnclosedOrphanedConnectionsAllAuths(); }

    public int getStatementCacheNumStatementsAllUsers() throws SQLException
    { return getPoolManager().getStatementCacheNumStatementsAllUsers(); }

    public int getStatementCacheNumCheckedOutStatementsAllUsers() throws SQLException
    { return getPoolManager().getStatementCacheNumCheckedOutStatementsAllUsers(); }

    public synchronized int getStatementCacheNumConnectionsWithCachedStatementsAllUsers() throws SQLException
    { return getPoolManager().getStatementCacheNumConnectionsWithCachedStatementsAllUsers(); }

    public void softResetAllUsers() throws SQLException
    { getPoolManager().softResetAllAuths(); }

    public int getNumUserPools() throws SQLException
    { return getPoolManager().getNumManagedAuths(); }

    public Collection getAllUsers() throws SQLException
    {
        LinkedList out = new LinkedList();
        Set auths = getPoolManager().getManagedAuths();
        for ( Iterator ii = auths.iterator(); ii.hasNext(); )
            out.add( ((DbAuth) ii.next()).getUser() );
        return Collections.unmodifiableList( out );
    }

    public synchronized void hardReset()
    {
        resetPoolManager(); 
    }

    public synchronized void close()
    { 
        resetPoolManager(); 
        is_closed = true;
        
        C3P0Registry.markClosed(this);

        if (Debug.DEBUG && Debug.TRACE == Debug.TRACE_MAX && logger.isLoggable(MLevel.FINEST))
        {
            logger.log(MLevel.FINEST, 
                    this.getClass().getName() + '@' + Integer.toHexString( System.identityHashCode( this ) ) +
                    " has been closed. ",
                    new Exception("DEBUG STACK TRACE for PoolBackedDataSource.close()."));
        }
    }

    /**
     * @deprecated the force_destroy argument is now meaningless, as pools are no longer
     *             potentially shared between multiple DataSources.
     */
    public void close(boolean force_destroy)
    { close(); }

    //other code
    public synchronized void resetPoolManager() //used by other, wrapping datasources in package, and in mbean package
    { resetPoolManager( true ); }

    public synchronized void resetPoolManager( boolean close_checked_out_connections ) //used by other, wrapping datasources in package, and in mbean package
    {
        if ( poolManager != null )
        {
            poolManager.close( close_checked_out_connections );
            poolManager = null;
        }
    }

    private synchronized ConnectionPoolDataSource assertCpds() throws SQLException
    {
        if ( is_closed )
            throw new SQLException(this + " has been closed() -- you can no longer use it.");

        ConnectionPoolDataSource out = this.getConnectionPoolDataSource();
        if ( out == null )
            throw new SQLException(NO_CPDS_ERR_MSG);
        return out;
    }

    private synchronized C3P0PooledConnectionPoolManager getPoolManager() throws SQLException
    {
        if (poolManager == null)
        {
            ConnectionPoolDataSource cpds = assertCpds();
            poolManager = new C3P0PooledConnectionPoolManager(cpds, null, null, this.getNumHelperThreads(), this.getIdentityToken());
            if (logger.isLoggable(MLevel.INFO))
                logger.info("Initializing c3p0 pool... " + this.toString()  /* + "; using pool manager: " + poolManager */);
        }
        return poolManager;	    
    }

    // serialization stuff -- set up bound/constrained property event handlers on deserialization
    private static final long serialVersionUID = 1;
    private static final short VERSION = 0x0001;

    private void writeObject( ObjectOutputStream oos ) throws IOException
    {
        oos.writeShort( VERSION );
    }

    private void readObject( ObjectInputStream ois ) throws IOException, ClassNotFoundException
    {
        short version = ois.readShort();
        switch (version)
        {
        case VERSION:
            setUpPropertyEvents();
            break;
        default:
            throw new IOException("Unsupported Serialized Version: " + version);
        }
    }
}
