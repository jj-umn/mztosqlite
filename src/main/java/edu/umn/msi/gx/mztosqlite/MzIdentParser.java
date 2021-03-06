/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.umn.msi.gx.mztosqlite;

import com.compomics.util.protein.Header;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import uk.ac.ebi.jmzidml.MzIdentMLElement;
import uk.ac.ebi.jmzidml.model.mzidml.AbstractParam;
import uk.ac.ebi.jmzidml.model.mzidml.AnalysisData;
import uk.ac.ebi.jmzidml.model.mzidml.AnalysisSoftware;
import uk.ac.ebi.jmzidml.model.mzidml.CvParam;
import uk.ac.ebi.jmzidml.model.mzidml.DBSequence;
import uk.ac.ebi.jmzidml.model.mzidml.DataCollection;
import uk.ac.ebi.jmzidml.model.mzidml.Modification;
import uk.ac.ebi.jmzidml.model.mzidml.Peptide;
import uk.ac.ebi.jmzidml.model.mzidml.PeptideEvidence;
import uk.ac.ebi.jmzidml.model.mzidml.PeptideEvidenceRef;
import uk.ac.ebi.jmzidml.model.mzidml.ProteinAmbiguityGroup;
import uk.ac.ebi.jmzidml.model.mzidml.ProteinDetectionHypothesis;
import uk.ac.ebi.jmzidml.model.mzidml.ProteinDetectionList;
import uk.ac.ebi.jmzidml.model.mzidml.SearchDatabase;
import uk.ac.ebi.jmzidml.model.mzidml.SourceFile;
import uk.ac.ebi.jmzidml.model.mzidml.SpectraData;
import uk.ac.ebi.jmzidml.model.mzidml.SpectrumIdentificationItem;
import uk.ac.ebi.jmzidml.model.mzidml.SpectrumIdentificationResult;
import uk.ac.ebi.jmzidml.model.mzidml.SubstitutionModification;
import uk.ac.ebi.jmzidml.model.mzidml.UserParam;
import uk.ac.ebi.jmzidml.xml.io.MzIdentMLUnmarshaller;

/**
 *
 * @author James E Johnson jj@umn.edu
 * @version 
 */
public class MzIdentParser {
    String[] FASTA_ID_PATTERNS  = {"^(?:sp|tr|gi[|][^|]+[|]ref)[|]([a-zA-Z0-9]+[|]*(:[_.][a-zA-Z0-9]+)*).*$",
    "^(\\w+)\\s*(.*)$"};
        
    String filepath;
    /* map of spectrum id to database primary key*/
    private Map<String,Object> spectrumIdPkidMap;
    private Map<String, String> accToSeq = new HashMap<>();
    private Map<String, String> accToDefline = new HashMap<>();
    boolean verbose = false;

    public MzIdentParser() {
    }

    public MzIdentParser(String filepath) {
        this(filepath,null);
    }

    public MzIdentParser(String filepath,Map<String,Object> spectrumIdPkidMap) {
        this.filepath = filepath;
        this.spectrumIdPkidMap = spectrumIdPkidMap;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    public void parseIdent(MzParserHandler handler) {
        parseIdent(filepath,handler);
    }
    public void parseIdent(String filepath, MzParserHandler parseHandler) {
        /*
        TODO: Generate scores for: 
          DBSequence: %sequence_coverage
            Spectral_identification: 
        */
        DateFormat dfmt = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSS");
        File input = new File(filepath);
        boolean aUseSpectrumCache = true;
        Map<String, SourceFile> sourceFileIdHashMap = new HashMap<>();
        Map<String, AnalysisSoftware> analysisSoftwareIdHashMap = new HashMap<>();
        Map<String, PeptideEvidence> peptideEvidenceIdHashMap = new HashMap<>();
        Map<String, Peptide> peptideIdHashMap = new HashMap<>();
        Map<String, SpectraData> spectraDataIdHashMap = new HashMap<>();
        Map<String, SpectrumIdentificationItem> siiIdHashMap = new HashMap<>();
        Map<String, SpectrumIdentificationResult> siiIdToSirHashMap = new HashMap<>();
        Map<String,Class> scoreNameToClassMap = new HashMap<>();
        Map<Integer, String> columnToScoreMap = new HashMap<>();        
        Map<Integer, String> columnToProtScoreMap = new HashMap<>();
        Map<String, DBSequence> dbSequenceIdHashMap = new HashMap<>();
        Map<String, ProteinDetectionHypothesis> pdhIdHashMap = new HashMap<>();
        Map<String, List<ProteinDetectionHypothesis>> peptide_pdh_HashMap = new HashMap<>();
        List<ProteinDetectionHypothesis> proteinDetectionHypothesisList = new ArrayList<>();
        List<ProteinAmbiguityGroup> proteinAmbiguityGroupList = new ArrayList<>();
        ProteinDetectionList proteinDetectionList = new ProteinDetectionList();
        boolean isAutoRefResolving = MzIdentMLElement.SpectrumIdentificationItem.isAutoRefResolving();
        // DB Maps for Foreign Keys
        Map<String,Object> fkSearchDatabaseIdPkid = new HashMap<>();
        Map<String,Object> fkDBSequenceIdPkid = new HashMap<>();
        Map<String,Object> fkSpectrumIdentificationIdPkid = new HashMap<>();
        Map<String,Object> fkPeptideIdPkid = new HashMap<>();
        Map<String,Map<String,Object>> spectrumIdentScores = new HashMap<>();
        Map<String,Class> scoreNameValueClass = new HashMap<>();
        // Read mzIdentML file
        MzIdentMLUnmarshaller unmarshaller = new MzIdentMLUnmarshaller(input);
        DataCollection dc = unmarshaller.unmarshal(DataCollection.class);
        AnalysisData ad = dc.getAnalysisData();
        List<SpectraData> spectraDataList = dc.getInputs().getSpectraData();
        for (SpectraData spectraData : dc.getInputs().getSpectraData()) {
            Map<String, Object> sourceMap = new HashMap<>();
            sourceMap.put("name", spectraData.getName());
            sourceMap.put("location", spectraData.getLocation());
            sourceMap.put("format", spectraData.getFileFormat().getCvParam().getName());
            sourceMap.put("spectrumIDFormat", spectraData.getSpectrumIDFormat() == null ? null : spectraData.getSpectrumIDFormat().getCvParam().getName());
            Object pkid = parseHandler.handle("SpectraData", sourceMap);
        }
        for (SearchDatabase searchDatabase : dc.getInputs().getSearchDatabase()) {
            Map<String, Object> sourceMap = new HashMap<>();
            sourceMap.put("name", searchDatabase.getName());
            sourceMap.put("location", searchDatabase.getLocation());  //required
            sourceMap.put("format", searchDatabase.getFileFormat() == null ? null : searchDatabase.getFileFormat().getCvParam().getName());
            sourceMap.put("numDatabaseSequences", searchDatabase.getNumDatabaseSequences());
            sourceMap.put("numResidues", searchDatabase.getNumResidues());
            sourceMap.put("releaseDate", searchDatabase.getReleaseDate());
            sourceMap.put("version", searchDatabase.getVersion());
            Object pkid = parseHandler.handle("SearchDatabase", sourceMap);
            fkSearchDatabaseIdPkid.put(searchDatabase.getId(), pkid);
        }
        // AnalysisSoftwareList  AnalysisSoftware SoftwareName
        if (verbose) {
            System.out.print(dfmt.format(new Date()));
            System.out.print("About to iterate over SourceFile");
        }
        Iterator<SourceFile> iterSourceFile = unmarshaller.unmarshalCollectionFromXpath(MzIdentMLElement.SourceFile);
        while (iterSourceFile.hasNext()) {
            SourceFile sourceFile = iterSourceFile.next();
            sourceFileIdHashMap.put(sourceFile.getId(), sourceFile);
            Map<String,Object> sourceMap = new HashMap<>();
            sourceMap.put("name", sourceFile.getName());
            sourceMap.put("location", sourceFile.getLocation());
            sourceMap.put("format", sourceFile.getFileFormat().getCvParam().getName());          
            Object pkid = parseHandler.handle("Source", sourceMap);
        }
        if (verbose) {
            System.out.println("...done");
            System.out.print(dfmt.format(new Date()));
            System.out.print("About to iterate over AnalysisSoftware");
        }
        Iterator<AnalysisSoftware> iterAnalysisSoftware = unmarshaller.unmarshalCollectionFromXpath(MzIdentMLElement.AnalysisSoftware);
        while (iterAnalysisSoftware.hasNext()) {
            AnalysisSoftware analysisSoftware = iterAnalysisSoftware.next();
            analysisSoftwareIdHashMap.put(analysisSoftware.getId(), analysisSoftware);            
        }
        if (verbose) {
            System.out.println("...done");
            System.out.print(dfmt.format(new Date()));
            System.out.print("About to iterate over DBsequence");
        }
        Iterator<DBSequence> iterDBSequence = unmarshaller.unmarshalCollectionFromXpath(MzIdentMLElement.DBSequence);
        while (iterDBSequence.hasNext()) {
            DBSequence dbSequence = iterDBSequence.next();
            dbSequenceIdHashMap.put(dbSequence.getId(), dbSequence);
            Object SearchDatabase_pkid = fkSearchDatabaseIdPkid.get(dbSequence.getSearchDatabaseRef());
            if (dbSequence.getSeq() == null) {
                String seq = accToSeq.get(dbSequence.getAccession());
                //System.out.println("seq: " + seq);
                if(seq!=null){
                    dbSequence.setSeq(seq);
                    if (dbSequence.getLength() == null) {
                        dbSequence.setLength(seq.length());
                    }
                }
            }
            Integer seqLen =dbSequence.getLength();
            Map<String, Object> dbMap = new HashMap<>();
            dbMap.put("SearchDatabase_pkid", SearchDatabase_pkid);
            dbMap.put("accession", dbSequence.getAccession());
            dbMap.put("length", dbSequence.getLength());
            dbMap.put("sequence", dbSequence.getSeq());
            for (CvParam cv : dbSequence.getCvParam()) {
                switch (cv.getAccession()) {
                    case "MS:1001088":
                        dbMap.put("description", cv.getValue()); // TEXT 
                        break;
                }
            }            
            Object pkid = parseHandler.handle("DBSequence", dbMap);
            fkDBSequenceIdPkid.put(dbSequence.getId(), pkid);
        }
        if (verbose) {
            System.out.println("...done");
            System.out.print(dfmt.format(new Date()));
            System.out.print("About to iterate over Peptide");
        }
        Iterator<Peptide> iterPeptide = unmarshaller.unmarshalCollectionFromXpath(MzIdentMLElement.Peptide);
        while (iterPeptide.hasNext()) {
            Peptide peptide = iterPeptide.next();
            peptideIdHashMap.put(peptide.getId(), peptide);
            Map<String, Object> peptideValues = new HashMap<>();
            List<Map<String, Object>> modValueList = new ArrayList<>();
            String peptideId = peptide.getId();
            peptideValues.put("sequence", peptide.getPeptideSequence());
            int modNum = 0;
            if (peptide.getModification() != null) {
                for (Modification mod : peptide.getModification()) {
                    modNum++;
                    Map<String, Object> modValues = new HashMap<>();
                    modValues.put("location", mod.getLocation());
                    List<String> residues = mod.getResidues();
                    StringBuilder sb = new StringBuilder();
                    for (String residue : residues) {
                        sb.append(residue);
                    }
                    modValues.put("residues", sb.length() > 0 ? sb.toString() : null);
                    modValues.put("replacementResidue", null);
                    modValues.put("avgMassDelta", mod.getAvgMassDelta());
                    modValues.put("monoisotopicMassDelta", mod.getMonoisotopicMassDelta());
                    String modNames = null;
                    for (CvParam cv : mod.getCvParam()) {
                        if (modNames == null) {
                            modNames = cv.getName();
                        } else {
                            modNames += "," + cv.getName();
                        }
                    }
                    modValues.put("name", modNames);
                    modValueList.add(modValues);
                }
            }
            if (peptide.getSubstitutionModification() != null) {
                for (SubstitutionModification subMod : peptide.getSubstitutionModification()) {
                    modNum++;
                    Map<String, Object> modValues = new HashMap<>();
                    modValues.put("location", subMod.getLocation());
                    modValues.put("residues", subMod.getOriginalResidue());
                    modValues.put("replacementResidue", subMod.getReplacementResidue());
                    modValues.put("avgMassDelta", subMod.getAvgMassDelta());
                    modValues.put("monoisotopicMassDelta", subMod.getMonoisotopicMassDelta());
                    modValues.put("name", "substitution");
                    modValueList.add(modValues);
                }
            }
            peptideValues.put("modNum", modNum);
            Object Peptide_pkid = parseHandler.handle("Peptide", peptideValues);
            fkPeptideIdPkid.put(peptideId, Peptide_pkid);
            for (Map<String, Object> modValues : modValueList) {
                modValues.put("Peptide_pkid", Peptide_pkid);
                parseHandler.handle("Modification", modValues);
            }
        }
        if (verbose) {
            System.out.println("...done");
            System.out.print(dfmt.format(new Date()));
            System.out.print("About to iterate over PepEvid");
        }
        Iterator<PeptideEvidence> iterPeptideEvidence = unmarshaller.unmarshalCollectionFromXpath(MzIdentMLElement.PeptideEvidence);
        while (iterPeptideEvidence.hasNext()) {
            PeptideEvidence peptideEvidence = iterPeptideEvidence.next();            
            peptideEvidenceIdHashMap.put(peptideEvidence.getId(), peptideEvidence);
        }
        if (verbose) {
            System.out.println("...done");
            System.out.print(dfmt.format(new Date()));
            System.out.print("About to iterate over Spectra Data");
        }
        Iterator<SpectraData> iterSpectraData = unmarshaller.unmarshalCollectionFromXpath(MzIdentMLElement.SpectraData);
        while (iterSpectraData.hasNext()) {
            SpectraData spectraData = iterSpectraData.next();
            spectraDataIdHashMap.put(spectraData.getId(), spectraData);
        }
        if (verbose) {
            System.out.println("...done");
            System.out.print(dfmt.format(new Date()));
            System.out.print("About to iterate over PDH");
        }
        Iterator<ProteinDetectionHypothesis> iterPDH = unmarshaller.unmarshalCollectionFromXpath(MzIdentMLElement.ProteinDetectionHypothesis);
        Integer pCounter = 0;
        while (iterPDH.hasNext()) {
            ProteinDetectionHypothesis pdh = iterPDH.next();
            pdhIdHashMap.put(pdh.getId(), pdh);

            for (CvParam cvParam : pdh.getCvParam()) {
                if (cvParam.getAccession().equals("MS:1001591") || cvParam.getAccession().equals("MS:1001592") || cvParam.getAccession().equals("MS:1001593")
                        || cvParam.getAccession().equals("MS:1001594") || cvParam.getAccession().equals("MS:1001595") || cvParam.getAccession().equals("MS:1001596")
                        || cvParam.getAccession().equals("MS:1001597")
                        || cvParam.getAccession().equals("MS:1001598")
                        || cvParam.getAccession().equals("MS:1001599")) {        //do nothing - these are specifically handled
                    //ToDO this code could be improved using an array of values...
                } else if (cvParam.getValue() != null) {
                    if (!columnToProtScoreMap.containsValue(cvParam.getName())) {
                        columnToProtScoreMap.put(pCounter, cvParam.getName());
                        pCounter++;
                    }
                }
            }
            for (UserParam userParam : pdh.getUserParam()) {
                if (!columnToProtScoreMap.containsValue(userParam.getName())) {
                    columnToProtScoreMap.put(pCounter, userParam.getName());
                    pCounter++;
                }

            }

        }
        Integer counter = 0;
        if (verbose) {
            System.out.println("...done");
            System.out.print(dfmt.format(new Date()));
            System.out.print("About to iterate over SIR");
        }
        Iterator<SpectrumIdentificationResult> iterSIR = unmarshaller.unmarshalCollectionFromXpath(MzIdentMLElement.SpectrumIdentificationResult);
        List<SpectrumIdentificationResult> sirList = new ArrayList<>();
        while (iterSIR.hasNext()) {
            SpectrumIdentificationResult sir = iterSIR.next();
            sirList.add(sir);

            List<SpectrumIdentificationItem> listSII = sir.getSpectrumIdentificationItem();

            for (SpectrumIdentificationItem sii : listSII) {
                siiIdHashMap.put(sii.getId(), sii);
                siiIdToSirHashMap.put(sii.getId(), sir);
                for (CvParam cvParam : sii.getCvParam()) {
                    String value = cvParam.getValue();
                    if (value != null && !value.isEmpty()) {
                        if (!columnToScoreMap.containsValue(cvParam.getName())) {
                            columnToScoreMap.put(counter, cvParam.getName());
                            Class colClass = String.class;
                            try {
                                int parseInt = Integer.parseInt(value);
                                colClass = Integer.class;

                            } catch (NumberFormatException exi) {
                                try {
                                    double parseDouble = Double.parseDouble(value);
                                    colClass = Double.class;
                                } catch (NumberFormatException exd) {

                                }
                            }
                            scoreNameToClassMap.put(cvParam.getName(), colClass);
                            counter++;
                        }
                    }
                }
            }
        }
        // Add new columns to score table
        parseHandler.addTableColumns("Score", scoreNameToClassMap);        
        if (verbose) {
            System.out.println("...done");
            System.out.print(dfmt.format(new Date()));
            System.out.print("About to create output");
        }
        for (SpectrumIdentificationResult sir : sirList) {
            String spectrumID = sir.getSpectrumID();
            SpectraData spectraData = spectraDataIdHashMap.get(sir.getSpectraDataRef());
            //String spectrumID = sir.getSpectrumID();
            Double rtInSeconds = -1.0;
            String spectrumTitle = null;
            // <cvParam accession="MS:1001114" name="retention time(s)"  cvRef="PSI-MS" pvalue="3488.676" unitAccession="UO:0000010" unitName="second" unitCvRef="UO" />
            //  <cvParam accession="MS:1000796" name="spectrum title"  cvRef="PSI-MS" pvalue="mam_050108o_CPTAC_study6_6E004.6805.6805.1" />
            //
            for (CvParam cvParam : sir.getCvParam()) {
                // Updated by FG: checking for old CV param 1114 or newer correct CV term 16.
                if (cvParam.getAccession().equals("MS:1001114") || cvParam.getAccession().equals("MS:1000016")) {
                    if (cvParam.getUnitAccession().equals("UO:0000010")) {
                        rtInSeconds = Double.parseDouble(cvParam.getValue());
                    } else if (cvParam.getUnitAccession().equals("UO:0000031")) {
                        rtInSeconds = Double.parseDouble(cvParam.getValue()) / 60;    //Convert minutes to seconds
                    } else {
                        System.out.println("Error parsing RT - unit not recognised");
                    }
                }
                if (cvParam.getAccession().equals("MS:1000796")) {
                    spectrumTitle = cvParam.getValue();
                }
            }

            List<SpectrumIdentificationItem> listSII = sir.getSpectrumIdentificationItem();
            for (SpectrumIdentificationItem sii : listSII) {
                // SpectrumIdentification (pkid INTEGER PRIMARY KEY, id TEXT, acquisitionNum INTEGER, chargeState INTEGER, rank INTEGER, passThreshold INTEGER, experimentalMassToCharge REAL, calculatedMassToCharge REAL
                //, sequence TEXT, modNum INTEGER, isDecoy INTEGER, post TEXT, pre TEXT, start INTEGER, end INTEGER, DatabaseAccess TEXT, DatabaseSeq TEXT, DatabaseDescription TEXT  )";
                // SpectrumIdentification (pkid INTEGER PRIMARY KEY, id TEXT, acquisitionNum INTEGER, chargeState INTEGER, rank INTEGER, passThreshold INTEGER, experimentalMassToCharge REAL, calculatedMassToCharge REAL, sequence TEXT, modNum INTEGER, isDecoy INTEGER)";
                Map<String, Object> psmValues = new HashMap<>();
                Map<String, Object> scoreValues = new HashMap<>();
                String indentificationID = sii.getId();
                //psmValues.put("id", spectrumID);         // TEXT
                if (spectrumIdPkidMap != null) {
                    if ((spectrumTitle != null) && (spectrumIdPkidMap.get(spectrumTitle) != null)) {
                        psmValues.put("Spectrum_pkid", spectrumIdPkidMap.get(spectrumTitle));
                    } else {
                        psmValues.put("Spectrum_pkid", spectrumIdPkidMap.get(spectrumID));
                    }
                }
                psmValues.put("spectrum_id", spectrumID);
                psmValues.put("acquisitionNum", null);                  // TEXT
                psmValues.put("chargeState", sii.getChargeState());              // INTEGER
                psmValues.put("retentionTime", rtInSeconds >= 0 ? rtInSeconds : null);              // INTEGER
                psmValues.put("rank", sii.getRank());                     // INTEGER
                psmValues.put("passThreshold", sii.isPassThreshold());            // INTEGER
                psmValues.put("experimentalMassToCharge", sii.getCalculatedMassToCharge()); // REAL 
                psmValues.put("calculatedMassToCharge", sii.getCalculatedMassToCharge());   // REAL 
                
//                Map<String, Object> mapNameToValue = new HashMap<>();
                //Handle scores 
                for (AbstractParam param : sii.getParamGroup()) {
                    String pname = param.getName();
                    String pvalue = param.getValue();
                    Object value = null;
                    if (pvalue != null && !pvalue.isEmpty()) {
                        Class colClass = scoreNameToClassMap.get(pname);
                        if (colClass == String.class) {
                            value = pvalue;
                        } else {
                            try {
                                if (colClass == Double.class) {
                                    value = new Double(pvalue);
                                } else if (colClass == Integer.class) {
                                    value = new Integer(pvalue);
                                }
                            } catch (NumberFormatException exd) {

                            }
                        }
                        //System.out.println("test1" + param.getName() + "-> " + param.getValue());
                    }
                    scoreValues.put(param.getName(), param.getValue());
                }
//                for (int i = 0; i < columnToScoreMap.size(); i++) {
//                    String score = columnToScoreMap.get(i);
//                    Object scoreValue = mapNameToValue.containsKey(score) ? mapNameToValue.get(score) : null;                    
//                    scoreValues.put(score, scoreValue);
//                }

                Object SpectrumIdentification_pkid = parseHandler.handle("SpectrumIdentification", psmValues);
                List<PeptideEvidenceRef> peptideEvidenceRefs = sii.getPeptideEvidenceRef();
                for (PeptideEvidenceRef peptideEvidenceRef : peptideEvidenceRefs) {
                    PeptideEvidence peptideEvidence = peptideEvidenceIdHashMap.get(peptideEvidenceRef.getPeptideEvidenceRef());
                    Object Peptide_pkid = fkPeptideIdPkid.get(peptideEvidence.getPeptideRef());
                    Object DBSequence_pkid = fkDBSequenceIdPkid.get(peptideEvidence.getDBSequenceRef());
                    Map<String, Object> peptideValues = new HashMap<>();
                    peptideValues.put("SpectrumIdentification_pkid", SpectrumIdentification_pkid);
                    peptideValues.put("DBSequence_pkid", DBSequence_pkid);
                    peptideValues.put("Peptide_pkid", Peptide_pkid);
                    peptideValues.put("pre", peptideEvidence.getPre());
                    peptideValues.put("post", peptideEvidence.getPost());
                    peptideValues.put("start", peptideEvidence.getStart());
                    peptideValues.put("end", peptideEvidence.getEnd());
                    peptideValues.put("isDecoy", peptideEvidence.isIsDecoy());
                    Object PeptideEvidence_pkid = parseHandler.handle("PeptideEvidence", peptideValues);
                }
                        
                if (!scoreValues.isEmpty()) {
                    scoreValues.put("spectrum_identification_id",spectrumID);
                    scoreValues.put("SpectrumIdentification_pkid", SpectrumIdentification_pkid);
                    parseHandler.handle("Score", scoreValues);
                }
            }

        }
        if (verbose) {
            System.out.println("...done");
            System.out.println(dfmt.format(new Date()));
        }
    }
    
    public void readFasta(String inputFasta) {
        List<Pattern> pats = new ArrayList<>();
        for (String pat : FASTA_ID_PATTERNS) {
            pats.add(Pattern.compile(pat));
        }
        InputStream fstream = null;
        try {
            fstream = new FileInputStream(inputFasta);
            // Get the object of DataInputStream
            InputStream in = new DataInputStream(fstream);
            BufferedReader br = new BufferedReader(new InputStreamReader(in));
            String line;
            String currSequence = "";
            String currProtAcc = null;
            String currDefline = null;
            int recordCounter = 0;
            while ((line = br.readLine()) != null)   {          
                line = line.replaceAll("\n","");                
                      
                if(line.contains(">")){                
                    //Insert previous into hash and reset
                    if(recordCounter != 0){              
                        currSequence = currSequence.replaceAll(" ","");
                        accToSeq.put(currProtAcc, currSequence);
                        //System.out.println("Inserting:" + currProtAcc + "_" + currSequence);
                        accToDefline.put(currProtAcc, currDefline);
                        //System.out.println("Inserting2:" + currProtAcc + "_" + currDefline);
                        
                        currSequence = "";
                    }   
                    
                    try {
                        Header header = Header.parseFromFASTA(line);
                        currProtAcc = header.getAccession();
                        currDefline = header.getDescription();
                    } catch (Exception ex) {
                        line = line.replaceAll(">", "");
                        for (Pattern p : pats) {
                            Matcher m = p.matcher(line);
                            if (m.matches()) {
                                switch (m.groupCount()) {
                                    case 2:
                                        currDefline = m.group(2);
                                    case 1:
                                        currProtAcc = m.group(1);
                                        break;
                                    default:
                                        currProtAcc = line;
                                }
                                break;
                            }
                        }
                    }
                    recordCounter++;
                } else {
                    currSequence += line;
                }
            }
            //handle last
            accToSeq.put(currProtAcc, currSequence.replaceAll(" ",""));
            accToDefline.put(currProtAcc, currDefline);
            //Close the input stream
            in.close();
        } catch (FileNotFoundException ex) {
              String methodName =Thread.currentThread().getStackTrace()[1].getMethodName();
             String className = this.getClass().getName();
             String message= "The task \""+methodName +  "\" in the class \""+ className + "\" was not completed because of "+ ex.getMessage()+"."+
                "\nPlease see the reference guide at 01 for more information on this error. https://code.google.com/p/mzidentml-lib/wiki/CommonErrors ";
             System.out.println (message);
        } catch (IOException ex) {
              String methodName =Thread.currentThread().getStackTrace()[1].getMethodName();
             String className = this.getClass().getName();
             String message= "The task \""+methodName +  "\" in the class \""+ className + "\" was not completed because of "+ ex.getMessage()+"."+
                "\nPlease see the reference guide at 02 for more information on this error. https://code.google.com/p/mzidentml-lib/wiki/CommonErrors ";
             System.out.println (message);
        } finally {
            try {
                fstream.close();
            } catch (IOException ex) {
                  String methodName =Thread.currentThread().getStackTrace()[1].getMethodName();
             String className = this.getClass().getName();
             String message= "The task \""+methodName +  "\" in the class \""+ className + "\" was not completed because of "+ ex.getMessage()+"."+
                "\nPlease see the reference guide at 02 for more information on this error. https://code.google.com/p/mzidentml-lib/wiki/CommonErrors ";
             System.out.println (message);
            }
        }        
    }

    public static void main(String[] args) {

        try {
            MzIdentParser mzIdentParser = new MzIdentParser();
        } catch (Exception ex) {
            System.err.println("ProteomeXchangeSubmission FAILED: " + ex.getMessage());
            System.exit(1);
        }
    }

}
