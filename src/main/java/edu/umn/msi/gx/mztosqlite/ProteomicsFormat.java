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
    FASTA(".fasta"),
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
        ProteomicsFormat fmt = null;
        FileReader fr = null;
        try {
            fr = new FileReader(inputFile);
            // read the first 1000 bytes
            char[] buffer = new char[1000];
            fr.read(buffer);
            String header = new String(buffer);
            String[] lines = header.split("\\r?\\n");
            //mzid
            if (header.contains("<MzIdentML")) {
                return MZID;
            }
            if (header.contains("http://regis-web.systemsbiology.net/pepXML")) {
                return PEPXML;
            }
            if (header.contains("<mzML")) {
                return MZML;
            }
            if (header.contains("<ExperimentCollection version=\"")) {
                return PRIDEXML;
            }
            // check for mzData
            if (header.contains("<mzData version=\"1.05\"")) {
                return MZDATA;
            }
            //check for mzXML
            if (header.contains("<mzXML")) {
                return MZXML;
            }
            //check for MGF
            if (header.contains("BEGIN IONS")) {
                return MGF;
            }
            //fasta
            for (String line : lines) {
                if (line.trim().length() > 0 && line.startsWith(">")) {
                    return FASTA;
                }
            }
     } finally {
            try {
                if (fr != null) {
                    fr.close();
                }
            } catch (IOException e) {
                // ignore
            }
        }
        return UNSUPPORTED;
    }

}
