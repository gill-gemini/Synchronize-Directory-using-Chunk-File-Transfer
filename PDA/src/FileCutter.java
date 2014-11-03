import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;

import org.apache.log4j.Logger;

import stack.Utils;


public class FileCutter {
	private static Logger logger = Logger.getLogger(FileCutter.class);
	
	static final int NumberOfChunksPerSession = 500;
	static final int NumberOfBytesPerChunk = 1000;
	/**
	 * @param args
	 * @throws IOException 
	 */
	public static void main(String[] args) throws IOException {
		String filePath = args[0];

		File file = new File(filePath);
		long fileSize = file.length();
		long numOfChunks = ((fileSize-1)/NumberOfBytesPerChunk) + 1;
		long numOfSessions = ((numOfChunks-1) / NumberOfChunksPerSession) + 1;
		int chunksInLastSession = (int) (numOfChunks % NumberOfChunksPerSession);

		for (int i = 1; i <= numOfSessions; i++) {
			long startingChunkId = (i - 1) * NumberOfChunksPerSession + 1;

			long endingChunkId = i * NumberOfChunksPerSession;
			if (i == numOfSessions) {
				endingChunkId = startingChunkId + chunksInLastSession -1;
			}

			// retrieve the chunks from the peer Host and save it in local dir
			// with some temporary file name
			// create a name for this temporary file
			String partFileName = filePath + ".cft_"+"part_"+i; 
			
			logger.debug("creating " +partFileName );
		
			createChunk(file, startingChunkId, endingChunkId, partFileName);
		}


		mergeFile(file.getParent(), file.getName(), numOfSessions);
		
	}
	private static int createChunk(File file, long startingChunkId, long endingChunkId,
			String partFileName) throws IOException {
		
		File newFile = new File(partFileName);
		newFile.createNewFile();
		
		FileOutputStream fout = new FileOutputStream(newFile);

		
		RandomAccessFile fileAccess = new RandomAccessFile(file, "r");
		byte[] chunkBuffer = new byte[NumberOfBytesPerChunk];

		long startingOffset = (startingChunkId - 1)
				* NumberOfBytesPerChunk;

		// move file pointer to the right position;
		fileAccess.seek(startingOffset);

		for (long chunkID = startingChunkId; chunkID < endingChunkId; chunkID++) {

			fileAccess.readFully(chunkBuffer);
			fout.write(chunkBuffer);
		}

		// deal with the last Chunk in this range, which maybe smaller than
		// NumberOfBytesPerChunk
		long numOfChunks = ((file.length() - 1) / NumberOfBytesPerChunk) + 1;
		if (endingChunkId < numOfChunks) {
			fileAccess.readFully(chunkBuffer);
			fout.write(chunkBuffer);

		} else {

			long num2bRead = file.length() - fileAccess.getFilePointer() ;
			byte[] lastChunk = new byte[(int) num2bRead];
			logger.debug("last chunk size is: "+ lastChunk.length);
			fileAccess.readFully(lastChunk);
			
			fout.write(lastChunk);
		}
		
		fileAccess.close();
		fout.close();

		return 0;
	}

	public static void mergeFile(String localDir, String fileName, long numOfSessions) throws IOException {
		// now collecting all the different "part files" and concatenate them together
		File finalFile = new File(localDir, "final_"+fileName);

		if (finalFile.exists()) {
			if (false == finalFile.delete()) {
				logger.error("cann't delete local file " + finalFile.getName());
			}
		}
		
		for (int i = 1; i <= numOfSessions; i++) {
			String partFileName = fileName + ".cft_"+"part_"+i;
			File partFile = new File(localDir, partFileName);

			Utils.concatFile(finalFile, partFile);
//			partFile.delete();
		}
	}
}
