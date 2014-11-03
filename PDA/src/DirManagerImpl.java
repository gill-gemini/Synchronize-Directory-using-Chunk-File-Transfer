import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

import stack.DirInfo;
import stack.FileInfo;
import stack.FileInfo.ModificationType;



public class DirManagerImpl implements DirectoryInfoManager {

	String dirName;	// the dir whose metadata is being maintained by this class
	
	public DirManagerImpl(String dirName) {
		super();
		this.dirName = dirName;
	}
	

	private static byte[] createChecksum(File file) throws Exception {
		InputStream fis = new FileInputStream(file);
		
		byte[] buffer = new byte[1024];
		MessageDigest complete = MessageDigest.getInstance("MD5");
		int numRead;

		do {
			numRead = fis.read(buffer);
			if (numRead > 0) {
				complete.update(buffer, 0, numRead);
			}
		} while (numRead != -1);

		fis.close();
		return complete.digest();
	}

	// see this How-to for a faster way to convert
	// a byte array to a HEX string
	private static String getMD5Checksum(File file) throws Exception {
		byte[] b = createChecksum(file);
		String result = "";

		for (int i = 0; i < b.length; i++) {
			result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
		}
		return result;
	}

	//
	private int InitMetaData(String path) throws Exception {

		try {
			File f = new File(path);
			if (f.isDirectory()) { // when it is a directory
				System.out.println("Directory found");

			} else {
				System.out.println("Wrong path!");
			}
			File file = new File("metadata");
			boolean exist = file.createNewFile();
			if (!exist)
				return -1;
			FileWriter fstream = new FileWriter("metadata");
			BufferedWriter out = new BufferedWriter(fstream);
			File flist[] = f.listFiles();
			for (int i = 0; i < flist.length; i++) {
				if (flist[i].isDirectory()) {
					InitMetaData(flist[i].getName());
				} else {
					out.write(flist[i].getName());
					out.write(' ');
					out.write(Long.toString(flist[i].length()));
					out.write(' ');
					out.write(String.valueOf(getMD5Checksum(flist[i])));
					out.write(' ');
					out.write(Long.toString(flist[i].lastModified()));
					out.write(' ');
					out.write("MODIFY");
					out.write('\n');
				}
			}
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

		return 0;
	}


	public DirInfo getLocalDirInfo() throws Exception {
		if (InitMetaData(dirName) == 0)
			System.out.println("metadata initialized!");
		File file = new File("metadata");
		DirInfo dirInfo = new DirInfo();

		FileInputStream fis = null;
		BufferedInputStream bis = null;
		DataInputStream dis = null;

		try {
			fis = new FileInputStream(file);
			// Here BufferedInputStream is added for fast reading.
			bis = new BufferedInputStream(fis);
			dis = new DataInputStream(bis);
			// dis.available() returns 0 if the file does not have more lines.
			while (dis.available() != 0) {
				FileInfo fileInfo = new FileInfo();
				// this statement reads the line from the file and print it to
				// the console.
				String buffer = dis.readLine();

				String[] sArray = buffer.split(" ");

				fileInfo.name = sArray[0].toString();
				fileInfo.size = Integer.parseInt(sArray[1]);
				fileInfo.checkSum = sArray[2];
				fileInfo.modificationTime = Long.parseLong(sArray[3]);
				if (sArray[4].equals("MODIFY")){
					fileInfo.modifcationType = ModificationType.MODIFY;}
				else if (sArray[4].equals("CREATE"))
					fileInfo.modifcationType = ModificationType.CREATE;
				else if (sArray[4].equals("DELETE"))
					fileInfo.modifcationType = ModificationType.DELETE;

				
				dirInfo.addFileInfo(fileInfo);

			}

			// dispose all the resources after using them.
			fis.close();
			bis.close();
			dis.close();

		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return dirInfo;
	}

	public static void main(String args[]) {
		DirManagerImpl g = new DirManagerImpl("lib");

		long startTime = System.currentTimeMillis();
		try {
			System.out.println("list files:");
			 
			DirInfo d = g.getLocalDirInfo();
			for (FileInfo fileInfo: d.getFiles()) {
				System.out.println(fileInfo.toString());
			}

			g.addFileToLocalDirectory("README.txt");
			d = g.getLocalDirInfo();
			for (FileInfo fileInfo: d.getFiles()) {
				System.out.println(fileInfo.toString());
			}
			
			
		} catch (Exception e) {
			e.printStackTrace();
		}
		long endTime = System.currentTimeMillis();
		System.out
				.println("Total time used:" + (endTime - startTime) + "ms...");
	}

	@Override
	public void addFileToLocalDirectory(String fileName) throws Exception {
		if (InitMetaData(dirName) == 0)
			System.out.println("metadata initialized!");
		
		String filePath = dirName + File.separator + fileName;
		
		File newfile = new File(filePath);
		if (newfile.exists()) {

			FileInfo newFileInfo = new FileInfo();
			newFileInfo.checkSum = getMD5Checksum(newfile);
			newFileInfo.modifcationType = FileInfo.ModificationType.CREATE;
			newFileInfo.modificationTime = newfile.lastModified();
			newFileInfo.name = newfile.getName();
			newFileInfo.size = newfile.length();

			addFileInfo(newFileInfo);
		}
	}

	@Override
	public void deleteFileFromLocalDirectory(String fileName) throws Exception {

		deleteFileFromLocalDirectory(fileName, System.currentTimeMillis());
	}

	@Override
	public void modifyFileFromLocalDirectory(String fileName) throws Exception {
		if (InitMetaData(dirName) == 0)
			System.out.println("metadata initialized!");

		String filePath = dirName + File.separator + fileName;

		File file = new File(filePath);
		if (file.exists()) {

			FileInfo newFileInfo = new FileInfo();
			newFileInfo.checkSum = getMD5Checksum(file);
			newFileInfo.modifcationType = FileInfo.ModificationType.MODIFY;
			newFileInfo.modificationTime = file.lastModified();
			newFileInfo.name = file.getName();
			newFileInfo.size = file.length();

			addFileInfo(newFileInfo);
		}
	}


	@Override
	public void addFileInfo(FileInfo fileInfo)  throws Exception {
		if (InitMetaData(dirName) == 0) {
			System.out.println("metadata initialized!");
		}

		try {
			FileWriter fstream = new FileWriter("metadata", true);
			BufferedWriter out = new BufferedWriter(fstream);
			out.write(fileInfo.name);
			out.write(' ');
			out.write(Long.toString(fileInfo.size));
			out.write(' ');
			out.write(fileInfo.checkSum);
			out.write(' ');
			out.write(Long.toString(fileInfo.modificationTime));
			out.write(' ');
			out.write(fileInfo.modifcationType.toString());
			out.write('\n');
			out.close();
		} catch (Exception e) {
			e.printStackTrace();
		}

	}


	@Override
	public void deleteFileFromLocalDirectory(String fileName, long modificationTime)  throws Exception {
		if (InitMetaData(dirName) == 0)
			System.out.println("metadata initialized!");

		String filePath = dirName + File.separator + fileName;

		File file = new File(filePath);
		if (file.exists()) {

			FileInfo newFileInfo = new FileInfo();
			newFileInfo.checkSum = getMD5Checksum(file);
			newFileInfo.modifcationType = FileInfo.ModificationType.DELETE;
			newFileInfo.modificationTime = modificationTime;
			newFileInfo.name = file.getName();
			newFileInfo.size = file.length();

			addFileInfo(newFileInfo);

			file.setWritable(true);

			// delete local file after meta data update
			if (false == file.delete()) {
				System.err.println("failed to delete file : "+ file.getName());
			}
		}
		
	}

}