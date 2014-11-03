

import java.io.File;
import java.io.IOException;
import java.net.URL;

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.xml.sax.SAXException;

public class XmlHelper {
	// our xsd is in the same directory as the class files
	private static String xsdFileName = "DirInfo.xsd";

	/**
	 * Checks whether the given XML file conforms to the XML Schema Definition
	 * file. Ref:
	 * http://www.ibm.com/developerworks/xml/library/x-javaxmlvalidapi
	 * /index.html
	 * 
	 * @param xmlFileName
	 *            the name of the XML file
	 * @param xsdFileName
	 *            the name of the xsd file
	 * @return true, if the xmlFile conforms to the xsd schema definition
	 * @throws Exception
	 */
	public static boolean isFileMatchingSchema(String xmlFileName)
			throws Exception {

		System.out.println("Working Directory = "
				+ System.getProperty("user.dir"));
		System.err.println("xmlFileName is: " + xmlFileName);
		System.err.println("xsdFileName is: " + xsdFileName);

		URL xsdFileUrl = XmlHelper.class.getClassLoader().getResource(
				xsdFileName);

		File xmlFile = new File(xmlFileName);
		if (!xmlFile.exists()) {
			throw new IOException("xmlFile" + "\"" + xmlFileName + "\""
					+ " doesn't exists");
		}

		// 1. Lookup a factory for the W3C XML Schema language
		SchemaFactory factory = SchemaFactory
				.newInstance("http://www.w3.org/2001/XMLSchema");

		// 2. Compile the schema.
		// Here the schema is loaded from a java.io.File, but you could use
		// a java.net.URL or a javax.xml.transform.Source instead.
		Schema schema;
		try {
			schema = factory.newSchema(xsdFileUrl);
		} catch (SAXException e) {
			throw new UnsupportedOperationException("Unexpected Exception", e);

		}

		// 3. Get a validator from the schema.
		Validator validator = schema.newValidator();

		// 4. Parse the document you want to check.
		Source source = new StreamSource(xmlFile);

		// 5. Check the document
		try {
			validator.validate(source);
			return true;
		} catch (SAXException ex) {
			System.err.println(ex.getMessage());
			return false;
		}

	}

	public static void main(String[] args) throws Exception {
		// check if a given xml file is valid against our xsd file
		// validate the given XML file comforms to our predefined schema

		String xmlFileName = args[0];

		if (isFileMatchingSchema(xmlFileName)) {
			System.out.println("\"" + xmlFileName
					+ "\" matches the schema definition");
		} else {
			System.out.println("\"" + xmlFileName
					+ "\" does not match the schema definition");
		}
	}
}
