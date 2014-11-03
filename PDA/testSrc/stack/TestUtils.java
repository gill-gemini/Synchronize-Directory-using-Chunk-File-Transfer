package stack;
import static org.junit.Assert.assertEquals;

import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import junit.framework.Assert;

import org.junit.Ignore;
import org.junit.Test;
import org.w3c.dom.Document;


public class TestUtils {

	@Test
	public void testIsInteger() {
		Assert.assertTrue(Utils.isInteger("200"));
		
		Assert.assertFalse(Utils.isInteger("200ABC"));
	}
	
	
	@Test
	public void testGetDirInfoFromXmlStr() throws Exception {
		DirInfo dirInfo = new DirInfo();
		dirInfo.dirName = "SharedDir";
		
		// add some 10 dummy file info
		for (int i=0; i<10; i++){
			FileInfo fileInfo = new FileInfo();
			fileInfo.name = "File-"+Integer.toString(i);
			fileInfo.size = i * 100 * 1000;
			fileInfo.checkSum = "ABCDE"+Long.toString(fileInfo.size);
			fileInfo.modifcationType = FileInfo.ModificationType.MODIFY;
			fileInfo.modificationTime = System.currentTimeMillis();

			dirInfo.addFileInfo(fileInfo);
		}
		
		String dirInfoInXmlStr = Utils.getDirInfoInXmlStr(dirInfo);
		
		DirInfo info = Utils.getDirInfoFromXmlStr(dirInfoInXmlStr);
		
		assertEquals(dirInfo.getFiles().size(), info.getFiles().size()); 
	}
	
	@Test
	public void testGetDirInfoInXmlStr() {
		DirInfo dirInfo = new DirInfo();
		dirInfo.dirName = "SharedDir";
		
		// add some 10 dummy file info
		for (int i=0; i<10; i++){
			FileInfo fileInfo = new FileInfo();
			fileInfo.name = "File-"+Integer.toString(i);
			fileInfo.size = i * 100 * 1000;
			fileInfo.checkSum = "ABCDE"+Long.toString(fileInfo.size);
			fileInfo.modifcationType = FileInfo.ModificationType.MODIFY;
			fileInfo.modificationTime = System.currentTimeMillis();

			dirInfo.addFileInfo(fileInfo);
		}
		
		String dirInfoInXmlStr = Utils.getDirInfoInXmlStr(dirInfo);
	
		System.err.println(dirInfoInXmlStr);
	}
	
	
	@Ignore
	public void testCreateXMLDoc() throws Exception {
		DirInfo dirInfo = new DirInfo();
		dirInfo.dirName = "SharedDir";
		
		// add some 100 dummy file info
		for (int i=0; i<100; i++){
			FileInfo fileInfo = new FileInfo();
			fileInfo.name = "File-"+Integer.toString(i);
			fileInfo.size = i * 100 * 1000;
			fileInfo.checkSum = "ABCDE"+Long.toString(fileInfo.size);
			fileInfo.modifcationType = FileInfo.ModificationType.MODIFY;
			fileInfo.modificationTime = System.currentTimeMillis();

			dirInfo.addFileInfo(fileInfo);
		}
		

		// make the info available as xml string
		Document xmlDoc = Utils.getXMLDocument(dirInfo);

		TransformerFactory transformerFactory = TransformerFactory
				.newInstance();
		Transformer transformer = transformerFactory.newTransformer();
		DOMSource source = new DOMSource(xmlDoc);
		StreamResult result = new StreamResult(System.out);
		transformer.transform(source, result); 		
	}
}
