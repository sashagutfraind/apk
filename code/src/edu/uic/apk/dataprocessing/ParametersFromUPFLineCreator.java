package edu.uic.apk.dataprocessing;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.Velocity;

import repast.simphony.parameter.DefaultParameters;
import repast.simphony.parameter.Parameters;
import repast.simphony.parameter.xml.XMLSweeperProducer;

public class ParametersFromUPFLineCreator {

	private static final String NEW_PARAMS_FILE_NAME = "newParameters.xml";
	Parameters params;
	String csvFilePath;

	String outPath;
	String outFile;
	String upfFilePath;
	int upfRunNumber;

	public ParametersFromUPFLineCreator(String batchParamsFilePath,
			String upfFilePath, int upfRunNumber, String outPath, String outFile) {
		try {
			File batchParamsFile = new File(batchParamsFilePath);
			XMLSweeperProducer producer = new XMLSweeperProducer(
					batchParamsFile.toURI().toURL());
			params = producer.getParameters();
			this.upfFilePath = upfFilePath;
			this.upfRunNumber = upfRunNumber;
			this.outFile = outFile == null ? NEW_PARAMS_FILE_NAME : outFile;
			this.outPath = outPath;

		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void create() {

		Charset charset = Charset.forName("UTF-8");
		String line = null;
		boolean found = false;
		try (BufferedReader reader = Files.newBufferedReader(
				Paths.get(upfFilePath), charset)) {

			while ((line = reader.readLine()) != null) {
				String runNum = line.substring(0, line.indexOf("\t")).trim();
				if (Integer.parseInt(runNum) == upfRunNumber) {
					found = true;
					break;
				}
			}
		} catch (IOException x) {
			System.err.format("IOException: %s%n", x);
		}

		if (found) {
			DefaultParameters params = new DefaultParameters(this.params);

			line = line.substring(line.indexOf("\t") + 1, line.length());
			String[] vals = line.split(",");
			for (String val : vals) {
				String[] param = val.split("\t");
				params.setValue(param[0].trim(), param[1].trim());
			}

			try {
				Properties props = new Properties();
				props.put("resource.loader", "class");
				props.put("class.resource.loader.description",
						"Velocity Classpath Resource Loader");
				props.put("class.resource.loader.class",
						"org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader");
				Velocity.init(props);

				try (FileWriter writer = new FileWriter(new File(outPath,
						outFile))) {
					VelocityContext context = new VelocityContext();
					context.put("parameters", params);
					context.put("NULL", Parameters.NULL);
					String templateFile = "parameters.vt";
					String template = getClass().getPackage().getName();
					template = template.replace('.', '/');
					template = template + "/" + templateFile;
					Velocity.mergeTemplate(template, "UTF-8", context,
							writer);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		} else {
			System.err.println("The specified upf run number " + upfRunNumber
					+ " was not found in " + upfFilePath);
		}

	}

	public static void main(String[] args) {
		new ParametersFromUPFLineCreator(
				"batch/batch_params_test1.xml",
				"/Users/jozik/work/APK/LHSworkspace/upf_LHS_params2015_12_10__17_29_37__SMALL.txt",
				1, "/Users/jozik/work/APK/LHSworkspace/parametersXmlFiles", null).create();


	}
}
