import stack.DirInfo;
import stack.FileInfo;

public class DirManageApp {

	public enum CommandType {
		add, delete, modify, getmetadata
	};

	public static void main(String[] args) throws Exception {
		// the argument should be "command localdir fileName"

//		if (args.length < 2) {
//			System.out.println("usage: DirManagerApp [add|delete|modify|getmetadata] dirName fileName");
//		}

		String command = args[0];
		String dirName = args[1];
		String fileName;
		DirectoryInfoManager dirManager = new DirManagerImpl(dirName);
		CommandType commandtype = CommandType.valueOf(command);
		switch (commandtype) {
		case add:
			fileName = args[2];
		
			// add the file in the local directory to metadata
			dirManager.addFileToLocalDirectory(fileName);
			break;
		case delete:
			fileName = args[2];
			
			// delete a file from local directory
			dirManager.deleteFileFromLocalDirectory(fileName);
			break;
		case modify:
			fileName = args[2];

			// the file in local directory has been modified. mark this in meta
			// file
			dirManager.modifyFileFromLocalDirectory(fileName);
			break;
		case getmetadata:
			DirInfo dirInfo = dirManager.getLocalDirInfo();
			for (FileInfo localFileInfo : dirInfo.getFiles()) {
				System.out.println(localFileInfo.toString());
			}			
			break;
		}

	}
}
