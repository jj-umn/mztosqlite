/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.umn.msi.gx.mztosqlite;

import java.util.Map;

/**
 *
 * @author James E Johnson jj@umn.edu
 * @version 
 */
public class MzParserHandler {
    MzSQLiteDB mzSQLiteDB = null;

    public MzParserHandler(MzSQLiteDB mzSQLiteDB) {
        this.mzSQLiteDB = mzSQLiteDB;
    }
    
    public Object handle(String tableName, Map<String,Object> values) {
        return this.mzSQLiteDB.insertOrReplace(tableName, values);
    }
    
    public void addTableColumns(String table,Map<String,Class> colTypes) {
        mzSQLiteDB.addTableColumns(table, colTypes);
    }

}
