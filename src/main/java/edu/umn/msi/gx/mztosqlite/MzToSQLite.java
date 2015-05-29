/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.umn.msi.gx.mztosqlite;

import java.io.File;
import java.io.IOException;
import static java.lang.System.exit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.commons.cli.BasicParser;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.cli.Parser;
import org.tmatesoft.sqljet.core.SqlJetException;

/**
 *
 * @author James E Johnson jj@umn.edu
 * @version
 */
public class MzToSQLite {
    Map<String, ProteomicsFormat> scanFiles = new HashMap<>();
    Map<String, ProteomicsFormat> identFiles = new HashMap<>();
    Map<String, ProteomicsFormat> seqDbFiles = new HashMap<>();
    String dbPath = null;
    String jsonPath = null;
    String tsvPath = null;
    boolean verbose = true;
    MzSQLiteDB mzSQLiteDB = null;

    public final void parseOptions(String[] args) {
        Integer MAX_INPUTS = 100;
        Parser parser = new BasicParser();
        String dbOpt = "sqlite";
        String inputFileOpt = "input";
        String inputNameOpt = "name";
        String inputIdOpt = "encoded_id";
        String verboseOpt = "verbose";
        String helpOpt = "help";
        Options options = new Options();
        options.addOption("s", dbOpt, true, "SQLite output file");
        options.addOption("v", verboseOpt, false, "verbose");
        options.addOption("h", helpOpt, false, "help");
        options.addOption("i", inputFileOpt, verbose, "input file");
        options.addOption("n", inputNameOpt, verbose, "name for input file");
        options.addOption("e", inputIdOpt, verbose, "encoded id for input file");
        options.addOption("f", inputIdOpt, verbose, "FASTA Search Database files");
        options.getOption(inputFileOpt).setArgs(MAX_INPUTS);
        options.getOption(inputNameOpt).setArgs(MAX_INPUTS);
        options.getOption(inputIdOpt).setArgs(MAX_INPUTS);
        // create the parser
        try {
            // parse the command line arguments
            CommandLine cli = parser.parse(options, args);
            if (cli.hasOption(helpOpt)) {
                HelpFormatter formatter = new HelpFormatter();
                formatter.printHelp( "java -jar MzToSQLite.jar [options] [proteomics_data_file ...]", options );
                exit(0);
            }
            if (cli.hasOption(verboseOpt)) {
                verbose = true;
            }
            if (cli.hasOption(dbOpt)) {
                dbPath = cli.getOptionValue(dbOpt);
                mzSQLiteDB = new MzSQLiteDB(dbPath);
                try {
                    mzSQLiteDB.createTables();
                } catch (SqlJetException ex) {
                    Logger.getLogger(MzToSQLite.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            List<String> argList = cli.getArgList();
            if (argList != null) {
                for (String filePath : argList) {
                    File inputFile = new File(filePath);
                    if (inputFile.canRead()) {
                        try {
                            ProteomicsFormat format = ProteomicsFormat.getFormat(inputFile);
                            switch (format) {
                                case MZID:
                                    identFiles.put(filePath, format);
                                    break;
                                case MZML:
                                case MGF:
                                case DTA:
                                case MS2:
                                case PKL:
                                case MZXML:
                                case XML_FILE:
                                case MZDATA:
                                case PRIDEXML:
                                    scanFiles.put(filePath, format);
                                    break;
                                case FASTA:
                                    seqDbFiles.put(filePath, format);
                                    break;
                                case PEPXML:
                                case UNSUPPORTED:
                                default:
                                    Logger.getLogger(MzToSQLite.class.getName()).log(Level.WARNING, "Unknown or unsupported format: {0}", filePath);
                                    break;
                            }
                        } catch (IOException ex) {
                            Logger.getLogger(MzToSQLite.class.getName()).log(Level.SEVERE, null, ex);
                        }
                    } else {
                        Logger.getLogger(MzToSQLite.class.getName()).log(Level.WARNING, "Unable to read {0}", filePath);
                    }
                }
            }
        } catch (ParseException exp) {
            Logger.getLogger(MzToSQLite.class.getName()).log(Level.SEVERE, null, exp);
        }

    }

    public void processFiles() {
        Map<String,Object> spectrumIdPkidMap = new HashMap<>();
        MzParserHandler handler = new MzParserHandler(this.mzSQLiteDB);
        for (String filepath : scanFiles.keySet()) {
            ProteomicsFormat format = scanFiles.get(filepath);
            Map<String,Object> source = new HashMap<>();
            source.put("name", filepath);
            source.put("location", filepath);
            source.put("format", format.toString());            
            handler.handle("Source", source);
            try {
                MzSpectrumParser mzSpectrum = new MzSpectrumParser(filepath, format, spectrumIdPkidMap);
                mzSpectrum.parseSpectrum(handler);
            } catch (Exception ex) {
                Logger.getLogger(MzToSQLite.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        for (String filepath : identFiles.keySet()) {
            ProteomicsFormat format = identFiles.get(filepath);
            Map<String,Object> source = new HashMap<>();
            source.put("name", filepath);
            source.put("location", filepath);
            source.put("format", format.toString());            
            handler.handle("Source", source);
            MzIdentParser mzIdentParser = new MzIdentParser(filepath,spectrumIdPkidMap);
            for (String fastapath : seqDbFiles.keySet()) {
                mzIdentParser.readFasta(fastapath);
            }            
            mzIdentParser.parseIdent(handler);
        }    
    }

    public static void main(String[] args) {
        try {
            MzToSQLite mzToSQLite = new MzToSQLite();
            mzToSQLite.parseOptions(args);
            mzToSQLite.processFiles();
        } catch (Exception ex) {
            Logger.getLogger(MzToSQLite.class.getName()).log(Level.SEVERE, null, ex);
            System.exit(1);
        }
    }

}
