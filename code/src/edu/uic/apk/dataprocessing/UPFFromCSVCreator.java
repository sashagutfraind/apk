package edu.uic.apk.dataprocessing;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import repast.simphony.parameter.ParameterSchema;
import repast.simphony.parameter.Parameters;
import repast.simphony.parameter.ParametersParser;
import repast.simphony.parameter.Schema;
import au.com.bytecode.opencsv.CSVReader;

public class UPFFromCSVCreator {

	private static final String UPF_FILE_NAME = "upf_from_csv.txt";
	
	Parameters parameters;
	String csvFilePath;

	String outPath;
	String outFile;

	public UPFFromCSVCreator(String paramsFilePath, String csvFilePath,
			String outPath, String outFile) {
		try {
			File paramsFile = new File(paramsFilePath);
			ParametersParser pp;
			pp = new ParametersParser(paramsFile);
			parameters = pp.getParameters();
			this.outFile = outFile == null ? UPF_FILE_NAME : outFile;
			this.csvFilePath = csvFilePath;
			this.outPath = outPath;

		} catch (ParserConfigurationException | SAXException | IOException e) {
			e.printStackTrace();
		}
	}

	public void create() {
		Charset charset = Charset.forName("UTF-8");
		Path path = Paths.get(outPath, outFile);
		List<String> paramNames = new ArrayList<>();
		for (String pName : parameters.getSchema().parameterNames()) {
			paramNames.add(pName);
		}

		try (BufferedWriter writer = Files.newBufferedWriter(path, charset);
				CSVReader reader = new CSVReader(new FileReader(csvFilePath))) {
			String[] nextLine;
			int lineNumber = 0;
			String[] csvParamNames = null;
			while ((nextLine = reader.readNext()) != null) {
				if (lineNumber == 0) {
					csvParamNames = nextLine;
				} else {
					setParameters(csvParamNames, nextLine);
					// nextLine[] is an array of values from the line
					writer.write(String.valueOf(lineNumber));
					writer.write("\t");

					boolean addComma = false;
					for (String pName : paramNames) {
						if (addComma)
							writer.write(",");
						writer.write(pName);
						writer.append("\t");
						writer.append(parameters.getValueAsString(pName));
						addComma = true;
					}
					writer.append("\n");
				}
				lineNumber++;
			}
			writer.flush();
		} catch (IOException x) {
			System.err.format("IOException: %s%n", x);
		}

	}

	private void setParameters(String[] csvParamNames, String[] nextLine) {
		Map<String, Object> pvs = new LinkedHashMap<String, Object>();
		Schema schema = parameters.getSchema();
		for (int i = 0; i < csvParamNames.length; i++) {
			String p = csvParamNames[i];
			ParameterSchema pSchema = schema.getDetails(p);
			if (pSchema == null){
				System.out.println("p: " + p + " has not schema" );
			}
			Object obj = pSchema.fromString(nextLine[i]);
			pvs.put(p, obj);
		}
		// set params with pvs
		for (String key : pvs.keySet()) {
			Object obj = pvs.get(key);
			parameters.setValue(key, obj);
		}
	}

	public static void main(String[] args) {
//		new UPFFromCSVCreator("APK.rs/parameters.xml", "/Users/jozik/work/APK/LHSworkspace/LHS_params2015_12_10__17_29_37__SMALL.csv", "/Users/jozik/work/APK/LHSworkspace/", "upf_LHS_params2015_12_10__17_29_37__SMALL.txt").create();
//		new UPFFromCSVCreator("APK.rs/parameters.xml", "/Users/jozik/work/APK/LHSworkspace/LHS_params2015_12_10__17_30_38__NORMAL.csv", "/Users/jozik/work/APK/LHSworkspace/", "upf_LHS_params2015_12_10__17_30_38__NORMAL.txt").create();
		new UPFFromCSVCreator("APK.rs/parameters.xml", "/Users/jozik/work/APK/LHSworkspace/LHS_params2015_12_14__21_17_25__100.csv", "/Users/jozik/work/APK/LHSworkspace/", "upf_LHS_params2015_12_14__21_17_25__100.txt").create();
	}
}
