/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.umn.msi.gx.mztosqlite;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import uk.ac.ebi.pride.tools.dta_parser.DtaFile;
import uk.ac.ebi.pride.tools.jmzreader.JMzReader;
import uk.ac.ebi.pride.tools.jmzreader.JMzReaderException;
import uk.ac.ebi.pride.tools.jmzreader.model.Spectrum;
import uk.ac.ebi.pride.tools.jmzreader.model.impl.CvParam;
import uk.ac.ebi.pride.tools.mgf_parser.MgfFile;
import uk.ac.ebi.pride.tools.ms2_parser.Ms2File;
import uk.ac.ebi.pride.tools.mzdata_parser.MzDataFile;
import uk.ac.ebi.pride.tools.mzml_wrapper.MzMlWrapper;
import uk.ac.ebi.pride.tools.mzxml_parser.MzXMLFile;
import uk.ac.ebi.pride.tools.mzxml_parser.MzXMLParsingException;
import uk.ac.ebi.pride.tools.pkl_parser.PklFile;
import uk.ac.ebi.pride.tools.pride_wrapper.PRIDEXmlWrapper;

/**
 *
 * @author James E Johnson jj@umn.edu
 * @version 
 */
public class MzSpectrumParser {
    String filepath;
    ProteomicsFormat format;
    Map<String,Object> spectrumIdPkidMap;
    
    public MzSpectrumParser() {
    }

    public MzSpectrumParser(String filepath, ProteomicsFormat format) {
        this.format = format;
        this.filepath = filepath;
    }

    public MzSpectrumParser(String filepath, ProteomicsFormat format,  Map<String, Object> spectrumIdPkidMap) {
        this.filepath = filepath;
        this.format = format;
        this.spectrumIdPkidMap = spectrumIdPkidMap;
    }

    public void parseSpectrum(MzParserHandler handler) throws Exception {
        parseSpectrum(filepath,format,handler);
    }

    public void parseSpectrum(String filepath, ProteomicsFormat format, MzParserHandler parseHandler) throws Exception {        
        File input = new File(filepath);
        JMzReader inputParser;
        if (format == null) {
            format = ProteomicsFormat.getFormat(input);
        }
        inputParser = getSpectrumParser(input, format);
        Iterator<Spectrum> scanIterator = inputParser.getSpectrumIterator();
        int nCurrentSpec = 0;
        while (scanIterator.hasNext()) {
            Spectrum s = scanIterator.next();
            Integer acquisitionNum = ++nCurrentSpec; // Default to ordinal in file
            try {
                /*
                MS:1000768 name: Thermo nativeID format def: "controller=xsd:nonNegativeInteger scan=xsd:positiveInteger."
                MS:1000769 name: Waters nativeID format def: "function=xsd:positiveInteger process=xsd:nonNegativeInteger scan=xsd:nonNegativeInteger."
                MS:1000770 name: WIFF nativeID format def: "sample=xsd:nonNegativeInteger period=xsd:nonNegativeInteger cycle=xsd:nonNegativeInteger experiment=xsd:nonNegativeInteger."
                MS:1000771 name: Bruker/Agilent YEP nativeID format def: "scan=xsd:nonNegativeInteger."
                MS:1000772 name: Bruker BAF nativeID format def: "scan=xsd:nonNegativeInteger."
                MS:1000773 name: "Bruker FID nativeID format": def: "xsd:string"
                MS:1000774 name: "multiple peak list nativeID format": def: "index=xsd:nonNegativeInteger"
                MS:1000775 name: "single peak list nativeID format": def: "xsd:string"
                MS:1000776 name: "scan number only nativeID format": def: "scan=xsd:nonNegativeInteger"
                */
                Integer scanNum = Integer.parseInt(s.getId().replaceAll("^.*?(\\d+)$","$1" ));
                acquisitionNum = scanNum;
            } catch (Exception e) {                
            }
            // make sure the spectrum isn't empty
            if (s == null) {
                continue;
            }
            String spectrumID = s.getId();
            Map<String, Object> spectrumValues = new HashMap<>();
            Map<Double, Double> peakList = s.getPeakList();
            int peakListLen = peakList != null ? peakList.size() : 0;
            spectrumValues.put("id", spectrumID);
            spectrumValues.put("acquisitionNum", acquisitionNum);
            spectrumValues.put("msLevel", s.getMsLevel());
            spectrumValues.put("polarity", null);
            spectrumValues.put("peaksCount", peakListLen);
            spectrumValues.put("totIonCurrent", null);
            spectrumValues.put("retentionTime", null);
            spectrumValues.put("basePeakMZ", null);
            spectrumValues.put("basePeakIntensity", null);
            spectrumValues.put("collisionEnergy", null);
            spectrumValues.put("ionisationEnergy", null);
            spectrumValues.put("lowMZ", null);
            spectrumValues.put("highMZ", null);
            spectrumValues.put("precursorScanNum", null);
            spectrumValues.put("precursorMZ", s.getPrecursorMZ());
            spectrumValues.put("precursorCharge", s.getPrecursorCharge());
            spectrumValues.put("precursorIntensity", s.getPrecursorIntensity());
            List<CvParam> cvParams = s.getAdditional().getCvParams();
            Map<String, Object> cvMap = new HashMap<>();
            for (CvParam cv : cvParams) {
                switch (cv.getAccession()) {
                    case "MS:1000285":
                        spectrumValues.put("totIonCurrent", cv.getValue());
                        break;
                    case "MS:1000504":
                        spectrumValues.put("basePeakMZ", cv.getValue());
                        break;
                    case "MS:1000505":
                        spectrumValues.put("basePeakIntensity",cv.getValue());
                        break;
                    case "MS:1000528":
                        spectrumValues.put("lowMZ", cv.getValue());
                        break;
                    case "MS:1000527":
                        spectrumValues.put("highMZ", cv.getValue());
                        break;
                    case "MS:1000894":
                        spectrumValues.put("retentionTime", cv.getValue());
                        break;
                        /* MGF: additional params
                        CvParam("retention time", retentionTime, "MS", "MS:1000894")
                        CvParam("peak list scans", scan, "MS", "MS:1000797")
                        CvParam("spectrum title", title, "MS", "MS:1000796")
                        CvParam("Fragment mass tolerance setting", tolerance.toString(), "PRIDE", "PRIDE:0000161")  
                        CvParam("Fragment mass tolerance setting", tolerance.toString(), "PRIDE", "PRIDE:0000161")                               
                        */
                    default:
                        break;
                }
            }
            
            Object spectrum_pkid = parseHandler.handle("Spectrum", spectrumValues);
            if (spectrumIdPkidMap != null) {
                spectrumIdPkidMap.put(spectrumID, spectrum_pkid);
            }
            List<Double> mozArray = new ArrayList<>();
            List<Double> intensityArray = new ArrayList<>();
            TreeSet<Double> keys = new TreeSet(peakList.keySet());
            for (Double moz : keys) {
                mozArray.add(moz);
                intensityArray.add(peakList.get(moz));
            }
            String moz = mozArray.toString().replaceAll(" ", "");            
            String intensity = intensityArray.toString().replaceAll(" ", "");
            Map<String,Object> peakValues = new HashMap<>();
            peakValues.put("acquisitionNum", acquisitionNum);
            peakValues.put("spectrum_pkid", spectrum_pkid);           
            peakValues.put("moz", moz);
            peakValues.put("intensity", intensity);
            Object peak_pkid = parseHandler.handle("Peaks", peakValues);            
        }
    }

    private JMzReader getSpectrumParser(File inputFile,ProteomicsFormat format) throws Exception {
        //hrow new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        JMzReader jMzReader = null;   
        if (format == null) {
            format = ProteomicsFormat.getFormat(inputFile.getName());
        }
        if (format != null) {
            try {
                switch (format) {
                    case MZML:
                        return new MzMlWrapper(inputFile);
                    case MGF:
                        return new MgfFile(inputFile);
                    case MZXML:
                        return new MzXMLFile(inputFile);
                    case DTA:
                        return new DtaFile(inputFile);
                    case MS2:
                        return new Ms2File(inputFile);
                    case PKL:
                        return new PklFile(inputFile);
                    case MZDATA:
                        return new MzDataFile(inputFile);
                    case PRIDEXML:
                        return new PRIDEXmlWrapper(inputFile);
                    default:
                        return null;
                }
            } catch (JMzReaderException | MzXMLParsingException e) {
                throw e;
            }
        }
        return jMzReader;
    }

    public static void main(String[] args) {

        try {
            MzSpectrumParser mzSpectrumParser = new MzSpectrumParser();
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
            System.exit(1);
        }
    }

}
