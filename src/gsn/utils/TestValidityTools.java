/**
* Global Sensor Networks (GSN) Source Code
* Copyright (c) 2006-2014, Ecole Polytechnique Federale de Lausanne (EPFL)
* 
* This file is part of GSN.
* 
* GSN is free software: you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation, either version 2 of the License, or
* (at your option) any later version.
* 
* GSN is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
* 
* You should have received a copy of the GNU General Public License
* along with GSN.  If not, see <http://www.gnu.org/licenses/>.
* 
* File: src/gsn/utils/TestValidityTools.java
*
* @author Ali Salehi
* @author Timotee Maret
*
*/

package gsn.utils;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import gsn.Main;
import gsn.beans.DataField;
import gsn.storage.StorageManager;

import java.sql.DriverManager;
import java.sql.SQLException;

import gsn.storage.StorageManagerFactory;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestValidityTools {

	static StorageManager sm = null;
	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		DriverManager.registerDriver( new org.h2.Driver( ) );
		sm = StorageManagerFactory.getInstance( "org.hsqldb.jdbcDriver","sa","" ,"jdbc:hsqldb:mem:.", Main.DEFAULT_MAX_DB_CONNECTIONS);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
	}

	@Before
	public void setUp() throws Exception {
	}

	@After
	public void tearDown() throws Exception {
	}

	@Test
	public void testIsAccessibleSocketStringInt() {

	}

	@Test
	public void testIsAccessibleSocketStringIntInt() {

	}

	@Test
	public void testCheckAccessibilityOfDirs() {

	}

	@Test
	public void testCheckAccessibilityOfFiles() {

	}

	@Test
	public void testIsDBAccessible() {

	}

	@Test
	public void testGetHostName() {

	}

	@Test
	public void testGetPortNumber() {

	}

	@Test
	public void testIsLocalhost() {
		assertTrue(ValidityTools.isLocalhost("127.0.0.1"));
		assertFalse(ValidityTools.isLocalhost("127.0.1.1"));
		assertTrue(ValidityTools.isLocalhost("localhost"));
		assertFalse(ValidityTools.isLocalhost("129.0.0.1"));
	}

	@Test
	public void testIsInt() {

	}

	@Test (expected=GSNRuntimeException.class)
	public void testTableExists() throws SQLException{
		assertFalse(sm.tableExists("myTable"));
		sm.executeCreateTable("table1",new DataField[]{},true);
		assertTrue(sm.tableExists("table1"));
		sm.executeDropTable("table1");
		assertFalse(sm.tableExists("table1"));
		assertFalse(sm.tableExists(""));
		assertFalse(sm.tableExists(null));
	}
	@Test (expected=GSNRuntimeException.class)
	public void testTableExistsWithEmptyTableName() throws SQLException{
		assertFalse(sm.tableExists(""));
	}
	@Test (expected=GSNRuntimeException.class)
	public void testTableExistsWithBadParameters() throws SQLException{
		assertFalse(sm.tableExists("'f\\"));
	}
	@Test
	public void testTablesWithSameStructure() throws SQLException{
		sm.executeCreateTable("table1",new DataField[]{},true);
		assertTrue(sm.tableExists("table1",new DataField[] {}));
		sm.executeDropTable("table1");
		sm.executeCreateTable("table1",new DataField[]{new DataField("sensor","double"),new DataField("sensor2","int")},true);
		assertTrue(sm.tableExists("table1",new DataField[] {new DataField("sensor","double")}));
		assertTrue(sm.tableExists("table1",new DataField[] {new DataField("sensor2","int")}));
		assertTrue(sm.tableExists("table1",new DataField[] {new DataField("sensor2", "int"),new DataField("sensor","double")}));
	}

}
