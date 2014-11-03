import stack.DirInfo;



/**
 * Some of the APIs that serves as a library to do directory synchronization
 * using CFT protocol
 * 
 * @author shaohong
 * 
 */
public interface SyncCFTAPI {
	
	public void getRemoteFile(String peerHostName, int peerPort,
			String fileName, long fileSize) throws Exception;
	
	/**
	 * get a file from peer syncCFT server
	 * 
	 * As a result, a file with the same name will be created in local directory
	 * and the meta data file will also be modified accordingly.
	 * 
	 * Exception will be thrown out if this process failed. and no local file will be created in that case.
	 */
	public void getRemoteFile(String peerHostName, int peerPort,
			String fileName, long fileSize, long modificationTime) throws Exception;

	/**
	 * retrieves the directory 
	 * @param peerHostName
	 * @param peerPort
	 * @param dirName
	 * @param timeStamp
	 * @return
	 */
	public DirInfo getRemoteDirInfo(String peerHostName, int peerPort, String dirName, long timeStamp) throws Exception;

	/**
	 * get local directory info
	 * @return
	 */
	public DirInfo getLocalDirInfo();

	/**
	 * run as syncCFT server
	 */
	public void runAsServer() throws Exception;	
}
