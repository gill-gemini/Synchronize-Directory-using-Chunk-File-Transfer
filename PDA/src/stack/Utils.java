package stack;


import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.w3c.dom.Text;


public class Utils {

	public static boolean isInteger(String s) {
		try {
			Integer.parseInt(s);
			return true;
		} catch (Exception e){
			return false;
		}
	}

	public static void concatFile(File finalFile, File partFile) throws IOException {
		FileOutputStream fout = new FileOutputStream(finalFile, true);
		FileInputStream fin = new FileInputStream(partFile);

		BufferedInputStream bi = new BufferedInputStream(fin);
		BufferedOutputStream bo = new BufferedOutputStream(fout);
		
		byte[] byteBuffer = new byte[5000];
		int bytesRead = bi.read(byteBuffer);
		while(-1 != bytesRead){
			bo.write(byteBuffer, 0, bytesRead);
			bytesRead = bi.read(byteBuffer);
		}
		
		bi.close();
		bo.close();
		fin.close();
		fout.close();
	}

	public static Document getXMLDocument(DirInfo localDirInfo) {
		
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder;
		try {
			builder = factory.newDocumentBuilder();
		} catch (ParserConfigurationException e) {
			e.printStackTrace();
			return null;
		}

		Document doc = builder.newDocument();
		Element dirElement = doc.createElement("directory");

		doc.appendChild(dirElement);
		

		for (FileInfo fileInfo:localDirInfo.getFiles()) {
			Element fileElement = doc.createElement("file");

			dirElement.appendChild(fileElement);
			
			Element element = doc.createElement("name");
			element.setTextContent(fileInfo.name);
			fileElement.appendChild(element);
			
			
			element = doc.createElement("size");
			element.setTextContent(Long.toString(fileInfo.size));
			fileElement.appendChild(element);
			
			element = doc.createElement("checkSum");
			element.setTextContent(fileInfo.checkSum);
			fileElement.appendChild(element);
			
			element = doc.createElement("modificationTime");
			element.setTextContent(Long.toString(fileInfo.modificationTime));
			fileElement.appendChild(element);

			
			element = doc.createElement("modificationType");
			element.setTextContent(fileInfo.modifcationType.toString());
			fileElement.appendChild(element);
		}
		
		return doc;
	}

	public static String getDirInfoInXmlStr(DirInfo localDirInfo) {
		Document xmlDoc = getXMLDocument(localDirInfo);

		TransformerFactory transformerFactory = TransformerFactory
				.newInstance();
		Transformer transformer;
		try {
			transformer = transformerFactory.newTransformer();

			DOMSource source = new DOMSource(xmlDoc);
			
			StringWriter stringWriter = new StringWriter();
			StreamResult result = new StreamResult(stringWriter);
			transformer.transform(source, result);
			
			return stringWriter.toString();
			
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();

			return null;
		}
	}
	
	
	public static byte[] getBytes(String aString) {
		try {
			return aString.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * convert an XML str representation of DirInfo to actual DirInfo
	 * @param xmlStr
	 * @return
	 * @throws Exception 
	 * @throws Exception 
	 */
	public static DirInfo getDirInfoFromXmlStr(String xmlStr) throws Exception {
		
		DirInfo dirInfo = new DirInfo();
		
		// We'll use the DOM API in JDK to traverse the XML file
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();

		DocumentBuilder builder = factory.newDocumentBuilder();
		
		// convert String into InputStream
	
		InputStream is = new ByteArrayInputStream(xmlStr.getBytes("UTF-8"));

		Document doc = builder.parse(is);

		Element root = doc.getDocumentElement();

		// The top level elements is "dir"
		NodeList filelist = root.getChildNodes();
//		int numberOfFiles = 0;
		for (int i = 0; i < filelist.getLength(); i++) {
			Node fileNode = filelist.item(i);

			if (fileNode instanceof Element) {
				
				FileInfo fileInfo = new FileInfo();
				
				Element fileElement = (Element) fileNode;

				// check and make sure the element tag name is "book".
				if (fileElement.getTagName().equals("file")) {
//					numberOfFiles++;
				} else {
					continue;
				}

//				System.out.print("#" + numberOfFiles + ". ");

				// Now let's iterate through the child nodes
				NodeList fileAttributes = fileElement.getChildNodes();
				for (int j = 0; j < fileAttributes.getLength(); j++) {
					Node fileAttr = fileAttributes.item(j);
					if (fileAttr instanceof Element) {
						String attrName = fileAttr.getNodeName();
						Text attrContent = (Text) fileAttr.getFirstChild();
						String attrValue = attrContent.getData().trim();
						
						if (attrName.equalsIgnoreCase("NAME")) {
							fileInfo.name =  attrValue;
						}
						
						if (attrName.equalsIgnoreCase("SIZE")) {
							fileInfo.size = Long.parseLong(attrValue);
						}
						
						if (attrName.equalsIgnoreCase("CHECKSUM")) {
							fileInfo.checkSum = attrValue;
						}
						
						if (attrName.equalsIgnoreCase("MODIFICATIONTIME")) {
							fileInfo.modificationTime = Long.parseLong(attrValue);
						}
						
						if (attrName.equalsIgnoreCase("MODIFICATIONTYPE")) {
							fileInfo.modifcationType = FileInfo.ModificationType.valueOf(attrValue);
						}

					}
				}

				dirInfo.addFileInfo(fileInfo);
			}
		}
		
		
		return dirInfo;
	}
}
