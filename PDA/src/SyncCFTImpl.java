import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

import org.apache.log4j.Logger;

import stack.CftRequest;
import stack.CftResponse;
import stack.CftSession;
import stack.CftTransaction;
import stack.ChunkRange;
import stack.ClientTransaction;
import stack.DirInfo;
import stack.Header;
import stack.LossModel;
import stack.ServerTransaction;
import stack.StackUser;
import stack.SyncCFTStack;
import stack.Utils;

/**
 * This is an implementation of the syncCFTAPI
 * 
 * It acts as the protocol stack user and do the file transfer stuff
 * 
 * @author shaohong
 * 
 */
public class SyncCFTImpl implements SyncCFTAPI, StackUser {

	private static Logger logger = Logger.getLogger(SyncCFTImpl.class);

	public enum RetrievalSessionState {
		Initial, PreparingToReceiveData, ReceivingData, RetrievedAllData, TimedOut, Interrupted, Failed
	};

	public enum SedingSessionState {
		Initial, WaitingACK, SendingData, TimedOut, Interrupted, Done
	};

	private SyncCFTStack theStack = null;

	String localDir;
	int localPort;

	static final int NumberOfChunksPerSession = 500;
	static final int NumberOfBytesPerChunk = 1000;

	Map<String, RetrievalSession> retrievalSessions = new Hashtable<String, RetrievalSession>();

	Map<String, SendingSession> sendingSessions = new Hashtable<String, SendingSession>();

	Map<String, SyncClientSession> syncClientSessions = new Hashtable<String, SyncClientSession>();
	
	LossModel packetLossModel;
	
	DirManagerImpl dirManager;
	
	public SyncCFTImpl(String localDir, int localPort) {
		super();
		this.localDir = localDir;
		this.localPort = localPort;

		dirManager = new DirManagerImpl(this.localDir);
		
	}

	/**
	 * initialize the syncCFTImpl, with the proper local port and the localDir
	 * 
	 * @param localPort
	 * @param localDir
	 * @throws Exception
	 */
	public void initialize() throws Exception {
		
		// set logging level to be same as root logger
		logger.setLevel(Logger.getRootLogger().getLevel());
//		System.out.println("setting log level to :" + logger.getLevel());

		if (null == theStack) {
			theStack = new SyncCFTStack(localPort);

			if (null != packetLossModel) {
				theStack.setLossModel(packetLossModel);
			}
			
			theStack.registerStackUser(this);

			theStack.startRunning();
		}
	}

	@Override
	public void getRemoteFile(String peerHostName, int peerPort,
			String fileName, long fileSize, long modificationTime) throws Exception {

		logger.debug("getting remote file " + fileName);
		// calculate how many sessions we needs to launch

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
			String partFileName = fileName + ".cft_"+"part_"+i; 
			
			logger.debug("getting " + fileName + " chunks " + startingChunkId + " - " + endingChunkId );
		
			int retrievalResult = retrieveChunks(peerHostName, peerPort,
					fileName, startingChunkId, endingChunkId, partFileName);
			
			if (retrievalResult != 0) {
				String errMsg = "failed to retrieve chunks: " + startingChunkId
						+ "-" + endingChunkId; 
				logger.error(errMsg);
				throw new IllegalStateException(errMsg);
			}
		}
		
		
		// now collecting all the different "part files" and concatenate them together
		File finalFile = new File(localDir, fileName);

		if (finalFile.exists()) {
			if (false == finalFile.delete()) {
				logger.error("cann't delete local file " + finalFile.getName());
			}
		}
		
		// set the appropriate modifcation time;
		if (false == finalFile.setLastModified(modificationTime)) {
			logger.error("failed to set modification time for " + finalFile.getName());
		}
		
		for (int i = 1; i <= numOfSessions; i++) {
			String partFileName = fileName + ".cft_"+"part_"+i;
			File partFile = new File(localDir, partFileName);

			Utils.concatFile(finalFile, partFile);
			
			partFile.delete();

		}
		
		// TODO: check received file has the same size and checksum as expected

}

	private int retrieveChunks(String peerHostName, int peerPort,
			String fileName, long startingChunkId,
			long endingChunkId, String partFileName) throws Exception {

		// create the RETRIEVE request and send it to remote peer
		CftSession session = theStack.createSession();

		// set peer's host/port info
		session.setPeerHost(peerHostName);
		session.setPeerPort(peerPort);

		CountDownLatch latch = new CountDownLatch(1);
		RetrievalSession retrievalSession = new RetrievalSession(session,
				fileName, startingChunkId, endingChunkId, latch);


		retrievalSession.setPartFileName(partFileName);

		retrievalSessions.put(session.getSessionID(), retrievalSession);
		retrievalSession.initiateSession();

		// wait for this Session to finish it's life cycle.
		latch.await();

		if (retrievalSession.isSuccessfullyDone()) {
			return 0; // successful retrieval;
		} else {
			return -1;
		}

	}

	private static String createChunkRangeStr(long startingChunkId,
			long endingChunkId) {
		StringBuffer sb = new StringBuffer();
		sb.append(startingChunkId);
		sb.append('-');
		sb.append(endingChunkId);

		return sb.toString();
	}

	@Override
	public DirInfo getRemoteDirInfo(String peerHostName, int peerPort,
			String dirName, long timeStamp) throws Exception {

		logger.info("getting remote directory info");
		// create the SYNC request and send it to remote peer
		CftSession session = theStack.createSession();

		// set peer's host/port info
		session.setPeerHost(peerHostName);
		session.setPeerPort(peerPort);

		CountDownLatch latch = new CountDownLatch(1);
		SyncClientSession syncClientSession = new SyncClientSession(session, dirName, timeStamp, latch);
		
		// save the session to get notified when data comes
		syncClientSessions.put(session.getSessionID(), syncClientSession);
		
		// launch the session to retrieve remote DIR INFO
		syncClientSession.initiateSession();

		// wait for this Session to finish its life cycle.
		latch.await();

		session.invalidate();
		return syncClientSession.getReceivedDirInfo();
	}

	@Override
	public void processRequest(CftRequest request) {
		logger.trace("processRequest " + request.getTheHeaderPart());

		if (request.getMethod().equals(CftRequest.RETRIEVE)) {
			ProcessRetrieveRequest(request);
		}
		
		
		if (request.getMethod().equals(CftRequest.DELIVER)) {
			RetrievalSession retrievalSession = retrievalSessions.get(request.getSessionID());
			if (retrievalSession != null) {
				retrievalSession.processDeliverRequest(request);
			} else {
				logger.error("received DELIVER request but no session is interested in handling it " + request.getHeader(Header.HN_ChunkID) );
			}
		}

		if (request.getMethod().equals(CftRequest.THANKS)) {
			SendingSession sendingSession = sendingSessions.get(request.getSessionID());
			if (null != sendingSession) {
				sendingSession.processTHANKS(request);
			} else {
				logger.debug("received THANKS but session is already gone!");
			}
		}
		
		if (request.getMethod().equals(CftRequest.ACK)) {
			SendingSession sendingSession = sendingSessions.get(request.getSessionID());
			try {
				sendingSession.processACK(request);
			} catch (Exception e) {
				logger.debug("meet exception handling ACK request", e); 
			}
		}
		
		if (request.getMethod().equals(CftRequest.SYNC)) {
			
			try {
				processSyncRequest(request);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
				

	}

	private void processSyncRequest(CftRequest request) throws Exception {
		logger.info("process SYNC:" + request.getRequestLine());

		String timeStampStr = Long.toString(System.currentTimeMillis());

		// get local directory information
		DirInfo localDirInfo = getLocalDirInfo();

		// make the info available as xml string
		String xmlDirInfo = Utils.getDirInfoInXmlStr(localDirInfo);

		byte[] msgBytes = xmlDirInfo.getBytes("UTF-8");
			
		int packetSize = 1000;
		int numberOfPackets = msgBytes.length / packetSize + 1;
		int lastPacketSize =  msgBytes.length % packetSize;
		if (0 == lastPacketSize) {
			lastPacketSize = packetSize;
			numberOfPackets --;
		}
			
		// send the data out in packets of 1K content
		for (int i=1; i<= numberOfPackets; i++) {
			// create 200 response here
			CftResponse response = request.createResponse(200, "OK");
			
			// set the appropriate "PN" header
			String packetNumber= i + "/" + numberOfPackets;
			response.setHeader(Header.HN_PN, packetNumber);
			
			// put the content inside the packet
			int startIdx = (i-1)*packetSize;
			int endIdx = startIdx + packetSize;
			
			if (i == numberOfPackets) {
				endIdx = startIdx + lastPacketSize;
			}
				
			byte[] content = Arrays.copyOfRange(msgBytes, startIdx, endIdx);
			response.setContent(content);
				
			// set the timestamp
			response.setHeader(Header.HN_Stamp, timeStampStr);
			ServerTransaction serverTx = (ServerTransaction) request
						.getTransaction();

			try {
				serverTx.sendResponse(response);
			} catch (IOException e) {
					logger.error("failed to send response", e);
			}

		}

	}

	@Override
	public DirInfo getLocalDirInfo() {
		try {
			return dirManager.getLocalDirInfo();
		} catch (Exception e) {
			logger.error(e);
			return null;
		}

	}

	private void ProcessRetrieveRequest(CftRequest request) {
		String fileName = request.getResourceName();
		File file = new File(localDir, fileName);

		if (!file.exists() || !file.canRead()) {
			sendResponse(request, 400, "Not Found");
			return;
		}

		String chunkRangeHdr = request.getHeader(Header.HN_ChunkRange);
		if (null == chunkRangeHdr) {
			sendResponse(request, 400, "Invalid Request");
			return;
		}

		// check whether the chunk range was correct
		ChunkRange chunkRange = ChunkRange
				.parseChunkRangeHeader(chunkRangeHdr);

		long numberOfChunks = (file.length() / NumberOfChunksPerSession) + 1;

		if (chunkRange.endChunkId > numberOfChunks) {
			sendResponse(request, 400, "Invalid ChunkRange");
			return;
		}

		// we depend on the session to handle retransmission and application
		// will only focus on application level logic
		
		CftSession session = request.getTransaction().getSession();
		SendingSession sendingSession = new SendingSession(session, file, chunkRange);
		
		// save the Session
		synchronized (this) {
			sendingSessions.put(session.getSessionID(), sendingSession);
		}
		
		try {
			sendingSession.processRetrieveRequest(request);
		} catch (Exception e) {
			logger.debug("meet exception handling RETRIEVE request", e); 
			
			//TODO: do something?
		}
	}

	private void sendResponse(CftRequest request, int statusCode,
			String reasonPhrase) {

		// simply generate a 200 OK response here
		CftResponse response = request.createResponse(statusCode, reasonPhrase);

		if (request.getMethod().equals(CftRequest.DELIVER)) {
			response.setHeader(Header.HN_ChunkID, request.getHeader(Header.HN_ChunkID));
		}
		
		ServerTransaction serverTx = (ServerTransaction) request
				.getTransaction();

		try {
			serverTx.sendResponse(response);
		} catch (IOException e) {
			logger.error("failed to send response", e);
		}
	}

	@Override
	public void processResponse(CftResponse response) {
//		logger.trace("processResponse " + response.getTheHeaderPart());
		logger.debug("processResponse " + response.getStatusCode() + " " + response.getHeader(Header.HN_ChunkID));

		// get the session associated with this response;
		String sessionID = response.getSessionID();
		
		if (response.getMethod().equals(CftRequest.RETRIEVE) || response.getMethod().equals(CftRequest.THANKS)) {
			RetrievalSession retrievalSession = retrievalSessions
					.get(sessionID);
			retrievalSession.processResponse(response);
		}
		
		
		if (response.getMethod().equals(CftRequest.DELIVER)) {
			SendingSession sendingSession = sendingSessions.get(sessionID);
			if (null != sendingSession) {
				sendingSession.processDeliverResponse(response);
			}
		}
		
		if (response.getMethod().equals(CftRequest.SYNC)) {
			SyncClientSession syncClientSession = syncClientSessions.get(sessionID);
			syncClientSession.processSyncResponse(response);
		}

	}

	@Override
	public void processTransactionTimeout(CftTransaction transaction) {
		
		if (transaction.isClientTx()) {
			
			ClientTransaction clientTx = (ClientTransaction) transaction;
			logger.debug("processTransactionTimeout for request\n"
					+ clientTx.getRequest().getTheHeaderPart());

			CftSession session = transaction.getSession();

			RetrievalSession retrievalSession = retrievalSessions.get(session.getSessionID());

			if (retrievalSession != null) {
				retrievalSession
						.processRetrievalTransactionTimeout(clientTx);
				retrievalSessions.remove(session.getSessionID());
			}
			
			SyncClientSession syncClientSession = syncClientSessions.get(session.getSessionID());
			if (syncClientSession != null) {
				syncClientSession.processTransactionTimeout(clientTx);
				syncClientSessions.remove(session.getSessionID());
			}
			
		} else {
			// on the data sender side, the retrieval transaction can timeout if
			// it sends 200/RETRIEVE and didn't receive ACK in time
			String sessionID = transaction.getSession().getSessionID();
			
			SendingSession sendingSession = sendingSessions.get(sessionID);
			
			if (null != sendingSession) {
				sendingSession.processTransactionTimeout(transaction);
			}
		}


	}

	/**
	 * block here and wait for the stackThread to exit
	 * 
	 * @throws Exception
	 */
	@Override
	public void runAsServer() throws Exception {

		initialize();

		Thread stackThread = theStack.getStackThread();

		// wait for the stackThread to exit;
		try {
			stackThread.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * do some cleanup stuff. such as closing the socket
	 */
	public void cleanUp() {
		theStack.shutDown();
	}

	/**
	 * State machine of application level Retrieval Session
	 * 
	 */
	class RetrievalSession {

		CftSession session;
		String fileName;
		long startingChunkID;
		long endingChunkID;
		CountDownLatch latch; // this is the semaphore objects that keeps our
								// threads in sync
		RetrievalSessionState state;
		
		byte[][] retrievalBuffer ; 
		BitSet chunkReceived;

		String partFileName;
		String clientDir;
		
		public String getPartFileName() {
			return partFileName;
		}

		public void setClientDir(String dirName) {
			clientDir = dirName;
		}

		public String getClientDir() {
			return clientDir;
		}
		
		public void setPartFileName(String partFileName) {
			this.partFileName = partFileName;
		}

		public RetrievalSession(CftSession session, String fileName,
				long startingChunkID, long endingChunkID, CountDownLatch latch) {
			super();
			this.session = session;
			this.fileName = fileName;
			this.startingChunkID = startingChunkID;
			this.endingChunkID = endingChunkID;
			this.latch = latch;

			state = RetrievalSessionState.Initial;
			
			long numOfChunks = (endingChunkID - startingChunkID + 1);
			
//			System.err.println(startingChunkID + " - " + endingChunkID + ":" + numOfBuffer);
			
			retrievalBuffer = new byte[(int)numOfChunks][];
			
			chunkReceived = new BitSet((int)numOfChunks);
		}

		public synchronized void processDeliverRequest(CftRequest request) {
			
			logger.trace("processing Deliver request " + request.getTheHeaderPart());
			logger.debug("receive ChunkID: " + request.getHeader(Header.HN_ChunkID) + "  " + request.getContent().length);
			
			//TODO validate the request
			
			//send out a 200 response;
			sendResponse(request, 200, "OK");
			
			String chunkIDstr = request.getHeader(Header.HN_ChunkID);
			long chunkID = Long.parseLong(chunkIDstr);
			
			int bufferIdx = (int) (chunkID - startingChunkID);

			if (chunkReceived.get(bufferIdx)) {
				 // this is a retransmission of DELIVER request
				return;
			}
			
			retrievalBuffer[bufferIdx] = request.getContent();
			
			chunkReceived.set(bufferIdx);

			if (chunkID != endingChunkID) {
				if (request.getContent().length != NumberOfBytesPerChunk) {
					logger.error("wrong size bytes received!");
					tearDown();
				}
			}
			
			if (allChunksReceived()){
				logger.debug("All chunks received for this session " + session.getSessionID());
				
				state = RetrievalSessionState.RetrievedAllData;
				
				// send THANKS to peer
				sendTHANKS();
				
				try {
					saveToFile();
				} catch (Exception e) {
					logger.error("failed to save file :" + getPartFileName());
				}
				
				// we'll waiting for the THANKS transaction to end before terminating the retrieval session 
				
//				// notify waiters that this session has done it's job
//				latch.countDown();
			}

		}

		private void sendTHANKS() {
			ClientTransaction clientTx = session.createClientTransaction();
			CftRequest request = clientTx.createRequest(CftRequest.THANKS);
			request.setResourceName(fileName);

			// set the ChunkRange header
			String chunkRangeStr = createChunkRangeStr(startingChunkID,
					endingChunkID);
			request.setHeader(Header.HN_ChunkRange, chunkRangeStr);
			
			request.setHeader(Header.HN_NumberOfChunks, Integer.toString(chunkReceived.cardinality()));

			try {

				clientTx.sendRequest(request);
				
			} catch (IOException e) {
				logger.error("Failed to send request!", e);

				tearDown();
			}
			
		}

		/**
		 * Save the received data to file
		 * @return
		 * @throws Exception 
		 */
		private int saveToFile() throws Exception {
			File newFile = new File(localDir, getPartFileName());
			
			FileOutputStream fout = new FileOutputStream(newFile);

			for(int i=0; i< retrievalBuffer.length; i++){
				fout.write(retrievalBuffer[i]);
			}
			
			fout.close();
			return 0;
		
		}

		private boolean allChunksReceived() {
			return chunkReceived.cardinality() == (endingChunkID - startingChunkID + 1); 
		}

		public boolean isSuccessfullyDone() {
			return (state == RetrievalSessionState.RetrievedAllData);
		}

		public void processRetrievalTransactionTimeout(
				ClientTransaction clientTx) {

			logger.debug("Transaction timed out for :\n"
					+ clientTx.getRequest().getTheHeaderPart());
			
			if (state == RetrievalSessionState.RetrievedAllData) {
				// it's OK for the THANKS transaction to timeout
			} else {
				state = RetrievalSessionState.TimedOut;				
			}

			tearDown();
		}

		private void tearDown() {
			latch.countDown();
		}

		public void initiateSession() {
			ClientTransaction clientTx = session.createClientTransaction();
			CftRequest request = clientTx.createRequest(CftRequest.RETRIEVE);
			request.setResourceName(fileName);

			// set the ChunkRange header
			String chunkRangeStr = createChunkRangeStr(startingChunkID,
					endingChunkID);
			request.setHeader(Header.HN_ChunkRange, chunkRangeStr);

			try {
				clientTx.sendRequest(request);
				state = RetrievalSessionState.PreparingToReceiveData;
			} catch (IOException e) {
				logger.error("Failed to send request!", e);

				tearDown();
			}
		}

		public void processResponse(CftResponse response) {
			
			if (response.getMethod().equals(CftRequest.RETRIEVE)) {
				if (!response.isSuccessfulResponse()) {
					logger.error("Retrieval request failed");
					state = RetrievalSessionState.Failed;

					tearDown();
				} else {

					state = RetrievalSessionState.ReceivingData;
					logger.debug("Retrieval session changed to " + state);
				}
			}

			if (response.getMethod().equals(CftRequest.THANKS)) {
				logger.debug("Received 200/THANKS!");
				
				// now can safely remove all reference related to the states of this session
				retrievalSessions.remove(session.getSessionID());
				
				// ask the stack to invalidate this session
				session.invalidate();
				
				tearDown();
			}
		}

		public void processSessionTimeout() {
			if (state == RetrievalSessionState.RetrievedAllData) {
				// ignore the timeout if we already received all Data
			} else {
				state = RetrievalSessionState.TimedOut;
				logger.error("Session timeout and we haven't received all the data. So far received " + chunkReceived.cardinality());
			}

			// remove self from the retrievalSessions list
			retrievalSessions.remove(this.session.getSessionID());
			
			tearDown();
		}
		
		

	} // end of RetrievalSession

	/**
	 * 
	 * The main logic implementing Sender part of the CFT Session
	 * 
	 */
	class SendingSession {

		CftSession session;
		File file;
		ChunkRange chunkRange;

		SedingSessionState state;

		public SendingSession(CftSession session, File file,
				ChunkRange chunkRange) {
			super();
			this.session = session;
			this.file = file;
			this.chunkRange = chunkRange;

			state = SedingSessionState.Initial;
		}
		
		
		public synchronized void processACK(CftRequest request) throws Exception {
			
			logger.info("processACK: sender received " + request.getRequestLine());
			
			state = SedingSessionState.SendingData;

			// open the file, get the data chunks and deliver them
			logger.debug("start to transmiting chunks:"
					+ chunkRange.startChunkId + " - " + chunkRange.endChunkId);

			// creating a file for the things I sent out
//			String newPartFileName = file.getAbsolutePath() + "."+chunkRange.startChunkId + "." + chunkRange.endChunkId;
//			File newPartFile = new File(newPartFileName);
//			newPartFile.createNewFile();
//			FileOutputStream foutPartFile = new FileOutputStream(newPartFileName);
			
			RandomAccessFile fileAccess = new RandomAccessFile(file, "r");
			byte[] chunkBuffer = new byte[NumberOfBytesPerChunk];

			long startingOffset = (chunkRange.startChunkId - 1)
					* NumberOfBytesPerChunk;

			// move file pointer to the right position;
			fileAccess.seek(startingOffset);

			for (long chunkID = chunkRange.startChunkId; chunkID < chunkRange.endChunkId; chunkID++) {

				fileAccess.readFully(chunkBuffer);

				deliverChunk(chunkID, chunkBuffer);
				
//				foutPartFile.write(chunkBuffer); // also write it to output file
			}

			// deal with the last Chunk in this range, which maybe smaller than
			// NumberOfBytesPerChunk
			long numOfChunks = ((file.length() - 1) / NumberOfBytesPerChunk) + 1;
			if (chunkRange.endChunkId < numOfChunks) {
				fileAccess.readFully(chunkBuffer);

				deliverChunk(chunkRange.endChunkId, chunkBuffer);
//				foutPartFile.write(chunkBuffer); // also write it to output file

			} else {

				long num2bRead = file.length() - fileAccess.getFilePointer() ;
				byte[] lastChunk = new byte[(int) num2bRead];
				logger.debug("last chunk size is: "+ lastChunk.length);
				fileAccess.readFully(lastChunk);
				deliverChunk(chunkRange.endChunkId, lastChunk);
				
//				foutPartFile.write(lastChunk); // also write it to output file
			}
			
			fileAccess.close();
//			foutPartFile.close(); // close output file
		}


		public void processTHANKS(CftRequest request) {
			logger.info("processTHANKS sender received " + request.getRequestLine());
			
			// send 200 OK
			sendResponse(request, 200, "OK");
			
			// make state changes
			state = SedingSessionState.Done;
			
			//clear up the session, I think it's OK if the 200 got lost
			sendingSessions.remove(request.getSessionID());
			
			session.invalidate();
		}


		void processRetrieveRequest(CftRequest request) throws Exception {
			logger.info("processRetrieveRequest sender received " + request.getRequestLine());
			
			// send 200 response
			sendResponse(request, 200, "OK");

			// change state
			state = SedingSessionState.WaitingACK;
		}
		
		/**
		 * deliver the given chunk
		 * @param chunkID
		 * @param chunkBuffer
		 * @param length
		 */
		private void deliverChunk(long chunkID, byte[] chunkBuffer) {
			logger.debug("delivering chunk [" + chunkID + ", " + chunkBuffer.length + "]");
			
			ClientTransaction clientTx = session.createClientTransaction();
			
			CftRequest request = clientTx.createRequest(CftRequest.DELIVER);
			request.setResourceName(file.getName());

			// set the ChunkRange header
			String chunkRangeStr = createChunkRangeStr(chunkRange.startChunkId,
					chunkRange.endChunkId);
			request.setHeader(Header.HN_ChunkRange, chunkRangeStr);

			request.setHeader(Header.HN_ChunkID, Long.toString(chunkID));
			request.setTargetHost(session.getPeerHost());
			request.setTargetPort(session.getPeerPort());
			
			request.setContent( chunkBuffer );

			try {
				clientTx.sendRequest(request);

			} catch (IOException e) {
				logger.error("Failed to send DELIVER request!", e);
			}
			
		}


		void processDeliverResponse(CftResponse response) {
			
		}

		void processTransactionTimeout(CftTransaction transaction) {
			logger.error("transaction timed out for " + transaction.getTransactionID());
			
			// restore the session to Initial State
			state = SedingSessionState.Initial;
		}


		public void processSessionTimeout() {
			state = SedingSessionState.TimedOut;

			// remove self from the session list
			sendingSessions.remove(this.session.getSessionID());
			
		}
		
		
	}

	public void setLossModel(LossModel lossModel) {
		packetLossModel = lossModel;		
	}

	@Override
	public void processSessionTimeout(CftSession session) {
		logger.error("session timed out: " + session.getSessionID());
		
		RetrievalSession retrievalSession = retrievalSessions.get(session.getSessionID());
		if (null != retrievalSession) {
			retrievalSession.processSessionTimeout();
		} 
		
		SendingSession sendingSession = sendingSessions.get(session.getSessionID());
		if (null != sendingSession) {
			sendingSession.processSessionTimeout();
		}
	}

	@Override
	public void getRemoteFile(String peerHostName, int peerPort,
			String fileName, long fileSize) throws Exception {
		getRemoteFile(peerHostName, peerPort, fileName, fileSize, System.currentTimeMillis());
		
	}
}
