package edu.umn.msi.gx.mztosqlite;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * The supported peak list formats.
 * @author jg
 *
 */
public enum ProteomicsFormat {

    DTA(".dta"),
    MGF(".mgf"),
    MS2(".ms2"),
    PKL(".pkl"),
    MZXML(".mzXML"),
    XML_FILE(".xml"),
    MZML(".mzML"),
    MZID(".mzid"),
    MZDATA(".xml"),
    PRIDEXML(".xml"),
    PEPXML(".pep.xml"),
    UNSUPPORTED("?");

    private final String extension;

    private ProteomicsFormat(String extension) {
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
    }

    /**
     * Returns the expected file format based on the filename's extension.
     * Returns null in case the file type is unknown.
     *
     * @param filename
     * @return
     */
    public static ProteomicsFormat getFormat(String filename) {
        filename = filename.toLowerCase();

        for (ProteomicsFormat f : values()) {
            if (filename.endsWith(f.getExtension().toLowerCase())) {
                return f;
            }
        }
        return null;
    }
    public static ProteomicsFormat getFormat(File inputFile) throws IOException {
        if (checkHeader(inputFile, "<MzIdentML")) {
            return MZID;
        }
        if (checkHeader(inputFile, "http://regis-web.systemsbiology.net/pepXML")) {
            return PEPXML;
        }
        
        if (checkHeader(inputFile, "<mzML")) {
            return MZML;
        }
        if (checkHeader(inputFile, "<ExperimentCollection version=\"")) {
            return PRIDEXML;
        }
        // check for mzData
        if (checkHeader(inputFile, "<mzData version=\"1.05\"")) {
            return MZDATA;
        }
        //check for mzXML
        if (checkHeader(inputFile, "<mzXML")) {
            return MZXML;
        }
        //check for mzXML
        if (checkHeader(inputFile, "BEGIN IONS")) {
            return MGF;
        }
        return UNSUPPORTED;
    }

    public static boolean checkHeader(File file, String string) throws IOException {
        boolean match = false;
        FileReader fr = null;
        try {
            fr = new FileReader(file);
            // read the first 1000 bytes
            char[] buffer = new char[1000];
            fr.read(buffer);
            String header = new String(buffer);
            match = header.contains(string);
        } finally {
            try {
                if (fr != null) {
                    fr.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
        return match;
    }

}
