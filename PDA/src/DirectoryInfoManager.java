import stack.DirInfo;
import stack.FileInfo;


public interface DirectoryInfoManager {

	/**
	 * add file to local directory. modify the meta data accordingly
	 * 
	 * @param fileName
	 */
	public void addFileToLocalDirectory(String fileName)throws Exception;

	/**
	 * delete file from local directory. modify local meta data file accordingly
	 * 
	 * @param fileName
	 */
	public void deleteFileFromLocalDirectory(String fileName)throws Exception;

	/**
	 * modify local meta data file to add the information that local file has
	 * been modified
	 * 
	 * @param fileName
	 */
	public void modifyFileFromLocalDirectory(String fileName)throws Exception;

	public DirInfo getLocalDirInfo() throws Exception;

	/**
	 * add the given fileinfo to meta data
	 * @param file2bUpdate
	 * @throws Exception
	 */
	public void addFileInfo(FileInfo fileInfo) throws Exception;

	public void deleteFileFromLocalDirectory(String name, long modificationTime)  throws Exception;
}
