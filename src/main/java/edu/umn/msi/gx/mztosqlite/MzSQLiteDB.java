/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.umn.msi.gx.mztosqlite;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.tmatesoft.sqljet.core.SqlJetException;
import org.tmatesoft.sqljet.core.SqlJetTransactionMode;
import org.tmatesoft.sqljet.core.schema.ISqlJetColumnDef;
import org.tmatesoft.sqljet.core.schema.SqlJetConflictAction;
import org.tmatesoft.sqljet.core.table.ISqlJetCursor;
import org.tmatesoft.sqljet.core.table.ISqlJetTable;
import org.tmatesoft.sqljet.core.table.SqlJetDb;

/**
 *
 * @author James E Johnson jj@umn.edu
 * @version 
 */
public class MzSQLiteDB {
    public static final String CREATE_Source_TABLE = "CREATE TABLE Source (pkid INTEGER PRIMARY KEY ASC AUTOINCREMENT, name TEXT, format TEXT, spectrumIDFormat TEXT, location TEXT)";
    public static final String CREATE_SpectraData_TABLE = "CREATE TABLE SpectraData (pkid INTEGER PRIMARY KEY ASC AUTOINCREMENT, name TEXT, format TEXT, spectrumIDFormat TEXT, location TEXT)";
    public static final String CREATE_SearchDatabase_TABLE = "CREATE TABLE SearchDatabase (pkid INTEGER PRIMARY KEY ASC AUTOINCREMENT, name TEXT, format TEXT, numDatabaseSequences INTEGER, numResidues INTEGER, releaseDate TEXT, version TEXT, location TEXT)";
    public static final String CREATE_DBSequence_TABLE = "CREATE TABLE DBSequence ( pkid INTEGER PRIMARY KEY, id INTEGER, SearchDatabase_pkid INTEGER REFERENCES SearchDatabase(pkid), accession TEXT, description TEXT, length INTEGER, sequence TEXT)";
    public static final String CREATE_PeptideEvidence_TABLE = "CREATE TABLE PeptideEvidence (pkid INTEGER PRIMARY KEY, SpectrumIdentification_pkid INTEGER REFERENCES SpectrumIdentification(pkid), Peptide_pkid INTEGER REFERENCES Peptide(pkid), DBSequence_pkid INTEGER REFERENCES DBSequence(pkid), isDecoy INTEGER, pre TEXT, post TEXT, start INTEGER, end INTEGER)";
    public static final String CREATE_Peptide_TABLE = "CREATE TABLE Peptide ( pkid INTEGER PRIMARY KEY, sequence TEXT, modNum INTEGER)";
    public static final String CREATE_Modification_TABLE = "CREATE TABLE Modification (pkid INTEGER PRIMARY KEY, Peptide_pkid INTEGER REFERENCES Peptide(pkid),location INTEGER, residues TEXT, replacementResidue TEXT, name TEXT, avgMassDelta REAL, monoisotopicMassDelta REAL )";
    public static final String CREATE_Score_TABLE = "CREATE TABLE Score (pkid INTEGER PRIMARY KEY, spectrum_identification_id TEXT, SpectrumIdentification_pkid INTEGER REFERENCES SpectrumIdentification(pkid) )";
    public static final String CREATE_SpectrumIdentification_TABLE = "CREATE TABLE SpectrumIdentification (pkid INTEGER PRIMARY KEY, Spectrum_pkid INTEGER, spectrum_id TEXT, acquisitionNum INTEGER, chargeState INTEGER, retentionTime REAL,rank INTEGER, passThreshold INTEGER, experimentalMassToCharge REAL, calculatedMassToCharge REAL)";
//    public static final String CREATE_Fragmentation_TABLE = "CREATE TABLE Fragmentation (pkid INTEGER PRIMARY KEY, spectrum_identification_id TEXT, charge INTEGER, index TEXT)";
    public static final String CREATE_Spectrum_TABLE = "CREATE TABLE Spectrum (pkid INTEGER PRIMARY KEY, id TEXT, acquisitionNum INTEGER, msLevel INTEGER, polarity INTEGER, peaksCount INTEGER, totIonCurrent REAL, retentionTime REAL, basePeakMZ REAL, basePeakIntensity REAL, collisionEnergy REAL, ionisationEnergy REAL, lowMZ REAL, highMZ REAL, precursorScanNum INTEGER, precursorMZ REAL, precursorCharge INTEGER, precursorIntensity REAL )";
    public static final String CREATE_Peaks_TABLE = "CREATE TABLE Peaks (pkid INTEGER PRIMARY KEY, Spectrum_pkid INT REFERENCES Spectrum(pkid), acquisitionNum INTEGER, moz TEXT, intensity TEXT)";
    public static final String[] TABLE_DEFS = {CREATE_Source_TABLE, CREATE_SpectraData_TABLE, CREATE_SearchDatabase_TABLE, CREATE_DBSequence_TABLE, CREATE_Peptide_TABLE, CREATE_PeptideEvidence_TABLE, CREATE_Modification_TABLE, CREATE_SpectrumIdentification_TABLE, CREATE_Score_TABLE, CREATE_Spectrum_TABLE,CREATE_Peaks_TABLE};
    // Indexes
    public static final String CREATE_DBSequence_DB_Accession_INDEX = "CREATE INDEX DBSequence_db_accession_idx ON DBSequence(SearchDatabase_pkid,accession)";
    public static final String CREATE_DBSequence_Accession_INDEX = "CREATE INDEX DBSequence_acc_idx ON DBSequence(accession)";
    public static final String CREATE_Peptide_sequence_INDEX = "CREATE INDEX Peptide_sequence_idx ON Peptide(sequence)";
    public static final String CREATE_PeptideEvidence_FKEYs_INDEX = "CREATE INDEX PeptideEvidence_fkey_idx on PeptideEvidence (spectrumidentification_pkid,dbsequence_pkid,peptide_pkid)";
    
    public static final String[] INDEX_DEFS = {CREATE_DBSequence_Accession_INDEX, CREATE_Peptide_sequence_INDEX, CREATE_PeptideEvidence_FKEYs_INDEX};
    /* Sample Protein query:
    SELECT dbs.accession, dbs.description, count(si.pkid) AS ScanCount 
    FROM spectrum, peptide, DBSequence dbs, peptideevidence pe, spectrumidentification si, score 
    WHERE pe.dbsequence_pkid = dbs.pkid 
    AND pe.peptide_pkid = peptide.pkid 
    AND pe.spectrumidentification_pkid = si.pkid 
    AND si.spectrum_pkid = spectrum.pkid 
    AND score.spectrumidentification_pkid = si.pkid 
    GROUP BY dbs.accession 
    ORDER BY ScanCount DESC;
    */
    /* Sample PeptideSpetralMatch query:
    SELECT CAST(Score.'PeptideShaker PSM score' as Number) AS 'PeptideShaker PSM score',
    CAST(Score.'OMSSA:evalue' as Number) AS 'OMSSA:evalue',CAST(Score.'MS-GF:SpecEValue' as Number) AS 'MS-GF:SpecEValue',
    CAST(Score.'theoretical mass' as Number) AS 'theoretical mass',
    CAST(Score.'PeptideShaker PSM confidence' as Number) AS 'PeptideShaker PSM confidence',
    Spectrum.acquisitionNum,Spectrum.msLevel,Spectrum.polarity,Spectrum.peaksCount,Spectrum.totIonCurrent,
    Spectrum.retentionTime,Spectrum.basePeakMZ,Spectrum.basePeakIntensity,Spectrum.collisionEnergy,
    Spectrum.ionisationEnergy,Spectrum.lowMZ,Spectrum.highMZ,Spectrum.precursorScanNum,Spectrum.precursorMZ,
    Spectrum.precursorCharge,Spectrum.precursorIntensity,Spectrum.title,Peptide.sequence,Peptide.modNum,dbs.accession, si.pkid 
    FROM spectrum, peptide, DBSequence dbs, peptideevidence pe, spectrumidentification si, score 
    WHERE pe.dbsequence_pkid = dbs.pkid 
    AND pe.peptide_pkid = peptide.pkid 
    AND pe.spectrumidentification_pkid = si.pkid 
    AND si.spectrum_pkid = spectrum.pkid 
    AND score.spectrumidentification_pkid = si.pkid;
    */
    String dbFilePath = null;
    File dbFile = null;
    SqlJetDb sqlJetDb = null;
    Map<String, Map<String, ISqlJetColumnDef>> schemaMap = null;
    Map<String,String> columnNameMap = new HashMap<>();
    
    public String sanitizeColumnName(String name) {
        if (!columnNameMap.containsKey(name)) {
//            columnNameMap.put(name, name.replaceAll("[-:]", "_"));            
            columnNameMap.put(name, name.matches("[a-zA-Z_]\\w*") ? name : "[" + name + "]");
        }
        return columnNameMap.get(name);
    }

    public MzSQLiteDB(String dbFilePath) {
        this.dbFilePath = dbFilePath;
        this.dbFile = new File(dbFilePath);
        dbFile.delete();
        Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.FINE, "{0} writable {1}", new Object[]{dbFile.getAbsolutePath(), dbFile.canWrite()});
    }

    SqlJetDb getDB() throws SqlJetException {
        if (sqlJetDb == null) {
            sqlJetDb = SqlJetDb.open(dbFile, true);
        }
        if (!sqlJetDb.isOpen()) {
            sqlJetDb.open();
        }
        Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.FINER, "getDB: {0}", sqlJetDb.toString());
        return sqlJetDb;
    }

    public void createTables() throws SqlJetException {
        SqlJetDb db = getDB();
        createTables(db);
        db.close();
    }

    
    public void createTables(SqlJetDb db) throws SqlJetException {
        db.beginTransaction(SqlJetTransactionMode.WRITE);
        try {
            for (String createTableStmt : TABLE_DEFS) {
                Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.FINE, createTableStmt);
                db.createTable(createTableStmt);
            }
            for (String indexStmt : INDEX_DEFS) {
                db.createIndex(indexStmt);
            }
        } catch (SqlJetException ex) {
            Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.SEVERE, null, ex);
        } finally {
            db.commit();
        }
    }

    public Map<String, Map<String, ISqlJetColumnDef>> getSchemaMap() {
        if (this.schemaMap == null) {
            Map<String, Map<String, ISqlJetColumnDef>> schema = new HashMap<>();
            try {
                StringBuilder sb = new StringBuilder();
                SqlJetDb db = getDB();
                Set<String> tableNames = db.getSchema().getTableNames();
                for (String tableName : tableNames) {
                    sb.append(tableName).append("_FIELDS = [");
                    Map<String, ISqlJetColumnDef> tableMap = new HashMap<>();
                    schema.put(tableName, tableMap);
                    List<ISqlJetColumnDef> columns = db.getTable(tableName).getDefinition().getColumns();
                    for (ISqlJetColumnDef column : columns) {
                        String columnName = column.getName();
                        sb.append("'").append(columnName).append("'").append(",");
                        tableMap.put(columnName, column);
                        columnNameMap.put(columnName, columnName);
                    }
                    sb.append("]").append(System.getProperty("line.separator"));
                }
                this.schemaMap = schema;
                Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.FINER,sb.toString());
            } catch (SqlJetException ex) {
                Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        return this.schemaMap;
    }

    public void checkColumns(String tableName, Map<String, Object> values) {
        Map<String, ISqlJetColumnDef> tableMap = getSchemaMap().get(tableName);
        for (String field : values.keySet()) {
            String columnName = sanitizeColumnName(field);
            if (!tableMap.containsKey(columnName)) {
                addColumns(tableName, values);
                break;
            }
        }
    }
    
    public void addTableColumns(String tableName,Map<String,Class> colTypes) {
        try {         
            SqlJetDb db = getDB();
            db.getSchema().getTableNames();
            db.beginTransaction(SqlJetTransactionMode.WRITE);
            try {
                    ISqlJetTable table = db.getTable(tableName);
                for (String field : colTypes.keySet()) {
                    if (table.getDefinition().getColumn(field) == null) {
                        String columnName = sanitizeColumnName(field);
                        Class colClass = colTypes.get(field);
                        String fieldType = colClass.isAssignableFrom(Double.class) ? "REAL" : colClass.isAssignableFrom(Integer.class) ? "INTEGER" : "TEXT";
                        String alterTable = "ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + fieldType;
                        Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.FINE, alterTable);
                        db.alterTable(alterTable);
                        ISqlJetColumnDef column = table.getDefinition().getColumn(columnName);
                        getSchemaMap().get(tableName).put(field, column);
                    }
                }
            } catch (SqlJetException ex) {
                Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.SEVERE, null, ex);
            }
            db.commit();
            db.close();
        } catch (SqlJetException ex) {
            Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.SEVERE, null, ex);
        }
    
    }

    public void addColumns(String tableName,Map<String,Object> values) {
        try {         
            SqlJetDb db = getDB();
            db.getSchema().getTableNames();
            db.beginTransaction(SqlJetTransactionMode.WRITE);
            try {
                ISqlJetTable table = db.getTable(tableName);
                for (String field : values.keySet()) {
                    if (table.getDefinition().getColumn(field) == null) {
                        String columnName = sanitizeColumnName(field);
                        Object value = values.get(field);
                        if (value != null) {
                            String fieldType = value instanceof Integer ? "INTEGER" : value instanceof Number ? "REAL" : "TEXT";
                            String alterTable = "ALTER TABLE " +  tableName + " ADD COLUMN " + columnName + " " + fieldType;
                            Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.FINE, alterTable);
                            db.alterTable(alterTable);
                            ISqlJetColumnDef column = table.getDefinition().getColumn(columnName);
                            getSchemaMap().get(tableName).put(field, column);
                        }
                    }
                }
            } catch (SqlJetException ex) {
                Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.SEVERE, null, ex);
            }
            db.commit();
            db.close();
        } catch (SqlJetException ex) {
            Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    public Object insertOrReplace(String tableName, Map<String, Object> values) {
        Object rowid = null;
        try {
            checkColumns(tableName,values);
            Map<String,ISqlJetColumnDef> tableMap = getSchemaMap().get(tableName);
            Map<String,Object> insertValues = new HashMap<>();
            for (String field : tableMap.keySet()) {
                insertValues.put(field, null);
            }
            for (String field : values.keySet()) {
//                insertValues.put(sanitizeColumnName(field),values.get(field));
                insertValues.put(field,values.get(field));
            }
            SqlJetDb db = getDB();
            db.beginTransaction(SqlJetTransactionMode.WRITE);
            try {
                ISqlJetTable table = db.getTable(tableName);
                rowid = table.insertByFieldNamesOr(SqlJetConflictAction.REPLACE, insertValues);
            } catch (SqlJetException ex) {
                Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.SEVERE, insertValues.toString(), ex);
            }
            db.commit();
            db.close();
        } catch (SqlJetException ex) {
            Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.SEVERE, null, ex);
        }
        return rowid;
    }
    
    public Object update(String tableName, Object pkid, Map<String, Object> values) {
        Object rowid = null;
        try {
            checkColumns(tableName,values);
            SqlJetDb db = getDB();
            db.beginTransaction(SqlJetTransactionMode.WRITE);
            try {
                ISqlJetTable table = db.getTable(tableName);
                ISqlJetCursor lookup = table.lookup(null, pkid);
                lookup.updateByFieldNames(values);
                rowid = lookup.getRowId();
             } catch (SqlJetException ex) {
                Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.SEVERE, values.toString(), ex);
            }
            db.commit();
            db.close();
        } catch (SqlJetException ex) {
            Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.SEVERE, null, ex);
        }
        return rowid;
    }

    public Map<String, Object> getValues(String tableName, Object pkid, Map<String, Object> values) {
        Map<String, Object> rowValues = values != null ? values : new HashMap<String, Object>();
        try {
            SqlJetDb db = getDB();
            ISqlJetTable table = db.getTable(tableName);
            Map<String, ISqlJetColumnDef> tableMap = getSchemaMap().get(tableName);
            ISqlJetCursor lookup = table.lookup(null, pkid);
            for (String field : tableMap.keySet()) {
                rowValues.put(field, lookup.getValue(field));
            }
        } catch (SqlJetException ex) {
            Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.SEVERE, null, ex);
        }
        return rowValues;
    }

    public static void main(String[] args) {
        String dbpath = new File(args.length > 0 ? args[0] : "/Users/jj/tmp/mzSQLiteDB.sqlite").getAbsolutePath();
        Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.FINE, dbpath);
        try {
            MzSQLiteDB mzSQLiteDB = new MzSQLiteDB(dbpath);
            mzSQLiteDB.createTables();
            if (mzSQLiteDB.sqlJetDb != null && mzSQLiteDB.sqlJetDb.isOpen()) {
                    mzSQLiteDB.sqlJetDb.close();
            }
        } catch (SqlJetException ex) {
            Logger.getLogger(MzSQLiteDB.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
}
