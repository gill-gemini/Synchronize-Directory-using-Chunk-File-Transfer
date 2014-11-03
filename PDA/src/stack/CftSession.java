package stack;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.log4j.Logger;

/**
 * CFT session
 * @author shaohong
 *
 */
public class CftSession {

	private static Logger logger = Logger.getLogger(CftSession.class);

	public enum SessionState { Initial, PreparationToReceiveData, PreparingToSendData, ReceivingData, TransmittingData, ReceivedAllData, };
	
	private SessionState state;

	private String sessionID;
	
	private SyncCFTStack theStack;

	AtomicInteger cseqNumber = new AtomicInteger(1); // the sequence number for the request generated by this session

	String peerHost;
	int peerPort;
	
	private Map<String, ClientTransaction> clientTxs = new Hashtable<String, ClientTransaction>();
	private Map<String, ServerTransaction> serverTxs = new Hashtable<String, ServerTransaction>();
	
	public static final int SessionTimeout = CftTransaction.RetransmissionTime * 7;
	
	private ScheduledFuture<?> sessionTimeoutTask;

	private String resourceName;

	private long peerSequenceNumber;

	private ChunkRange chunkRange;

	public String getPeerHost() {
		return peerHost;
	}

	public void setPeerHost(String peerHost) {
		this.peerHost = peerHost;
	}

	public int getPeerPort() {
		return peerPort;
	}

	public void setPeerPort(int peerPort) {
		this.peerPort = peerPort;
	}

	public CftSession(String sessionID) {
		super();
		this.sessionID = sessionID;
		this.state = SessionState.Initial;
	}
	
	public int getAndIncrementCSeq() {
		return cseqNumber.getAndIncrement();
	}
	
	public int getCseqNumber() {
		return cseqNumber.get();
	}
	
	public String getSessionID() {
		return sessionID;
	}

	public void setSessionID(String sessionID) {
		this.sessionID = sessionID;
	}
	
	
	public SessionState getState() {
		return state;
	}

	public void setState(SessionState state) {
		this.state = state;
	}
	
	public ClientTransaction createClientTransaction() {
		ClientTransaction clientTx = new ClientTransaction();
		clientTx.setSession(this);
		
		return clientTx;
	}
	
	public ServerTransaction createServerTransaction(String transactionID) {
		ServerTransaction serverTX = new ServerTransaction();
		serverTX.setSession(this);
		serverTX.setTransactionID(transactionID);
		return serverTX;
	}
	
	public SyncCFTStack getTheStack() {
		return theStack;
	}

	public void setTheStack(SyncCFTStack theStack) {
		this.theStack = theStack;
	}

	public void sendRequest(CftRequest request) throws IOException {
		
		String txID = request.getHeader(Header.HN_CSeq);
		ClientTransaction clientTransaction = clientTxs.get(txID);
		if (null != clientTransaction){
			// this is a retransmission or ACK
			theStack.sendMessage(request);			

			return;
		}
		
		if (request.getMethod().equals(CftRequest.RETRIEVE)){
			
			// changing session state
			if (state == SessionState.Initial) {
				state = SessionState.PreparationToReceiveData;
		
			} else {
				String message = "Session State is PreparationToReceiveData and couldn't send RETRIEVE";
				throw new IllegalStateException(message);
			}
			
		}
		
		if (request.getMethod().equals(CftRequest.DELIVER)) {
			// change session state if necessary
			state = SessionState.TransmittingData;

			logger.debug("delivering Chunk :"
					+ request.getHeader(Header.HN_ChunkID)
					+ request.getContent().length);
		}
		
		// save the client transaction;
		clientTxs.put(txID, (ClientTransaction) request.getTransaction());

		theStack.sendMessage(request);		
	}
	
	public void receiveResponse(CftResponse response) {
		if (response.getMethod().equals(CftRequest.RETRIEVE)) {
			
			if (state == SessionState.PreparationToReceiveData) {
				if (response.getStatusCode() == 200) {
					state = SessionState.ReceivingData;
				} else {
					state = SessionState.Initial;
				}
			}

		}
	}

	public void processMessage(CftMessage receivedMsg) {

		if (receivedMsg.isRequest()) {
			processRequest((CftRequest) receivedMsg);
		}
		else {
			processResponse((CftResponse) receivedMsg);
		}

	}

	private void processResponse(CftResponse response) {
		// we need to let clientTrasction know that response has been received
		
		String transactionID = response.getHeader(Header.HN_CSeq);
		ClientTransaction clientTransaction = clientTxs.get(transactionID);
		
		// associate this response with the corresponding transaction
		response.setTransaction(clientTransaction);
		
		if (null == clientTransaction) {
			logger.error("didn't find client transaction to handle " + response.getTheHeaderPart());
		}
		clientTransaction.processResponse(response);
		
		if (response.isSuccessfulResponse() && response.getMethod().equals(CftRequest.SYNC)) {
			// reset session timer for successful SYNC response
			state = SessionState.ReceivingData;
			resetSessionTimerForDataReceiver();
		}

		//TODO: make some state change if necessary
		if (response.getMethod().equals(CftRequest.THANKS)) {
			// this means this session has served its purpose
			tearDown();
		}		
	}

	private void tearDown() {
		state = SessionState.Initial;
		if (null != sessionTimeoutTask) {
			sessionTimeoutTask.cancel(true);
		}
	}

	private void processRequest(CftRequest request) {
		
		if (request.getMethod().equals(CftRequest.THANKS)) {
			if (SyncCFTStack.RobustnessEnhanced) {
				if (!isValidThanks(request)) {
					throw new IllegalArgumentException("invalid THANKS request");
				}
			}
		}
		
		// update sessionState
		if (request.getMethod().equals(CftRequest.RETRIEVE)){
			processRetrieve(request);
		}
		
		//create server transaction for this request
		String transactionID = request.getHeader(Header.HN_CSeq);
		ServerTransaction serverTx = serverTxs.get(transactionID);

		// if the request is ACK, then the server transaction shall already exits
		// in other cases, we need to create server transaction
		if (serverTx == null) {
		
			serverTx = createServerTransaction(transactionID);

			// save this server transaction
			serverTxs.put(transactionID, serverTx);
		}
		
		// associate the request with the server transaction
		request.setTransaction(serverTx);

		// let the transaction update its state;
		serverTx.processRequest(request);

		if (state == SessionState.ReceivingData) {
			resetSessionTimerForDataReceiver();		
		}
	}

	private synchronized void resetSessionTimerForDataReceiver() {
		// reset the session timeout task
		if (sessionTimeoutTask != null) {
			sessionTimeoutTask.cancel(true);			
		}
		sessionTimeoutTask = SyncCFTStack.scheduler.schedule(
				new SessionTimeoutTask(), SessionTimeout,
				TimeUnit.MILLISECONDS);
	}

	
	private void processRetrieve(CftRequest request) {
		logger.debug("processRetrieve for session:" + request.getSessionID());
		if (state == SessionState.Initial) {
			state = SessionState.PreparingToSendData;

			// save chunk range for robustness enhancement
			this.chunkRange = ChunkRange.parseChunkRangeHeader(request
					.getHeader(Header.HN_ChunkRange));

		} else {

			// check if this is a retransmit
			throw new IllegalStateException("can not handle "
					+ request.getMethod() + " in state:" + state);
		}

	}

	public void sendResponse(CftResponse response) throws IOException {
		
		// sending response for RETRIEVE
		if (response.getMethod().equals(CftRequest.RETRIEVE)){
			if (state == SessionState.PreparingToSendData) {
				
				if (response.isSuccessfulResponse()) {
					state = SessionState.PreparingToSendData ;
				} else {
					state = SessionState.Initial;
				}
			} else {
				String message = "Session State is " + state + " and couldn't send " + response.getMethod();
				throw new IllegalStateException(message);
			}
		}
		
		// sending response for DELIVER
		if (response.getMethod().equals(CftRequest.DELIVER)){
			
			//TODO: any state checking?
			state = SessionState.ReceivingData;
				
		}		

		// sending response for THANKS
		if (response.getMethod().equals(CftRequest.THANKS)){
			
			state = SessionState.Initial;
				
		}		

		theStack.sendMessage(response);
		
	}

	// the given transaction has timed out
	public void notifyTransactionTimeout(CftTransaction transaction) {
		
//		if (transaction.isClientTx()) {
//
//			state = SessionState.Initial;
////			ClientTransaction clientTx = (ClientTransaction) transaction;
////			// client transaction timeout, session should change its state
////			// accordingly
////			if (state == SessionState.PreparationToReceiveData) {
////				if (clientTx.getRequest().getMethod()
////						.equals(CftRequest.RETRIEVE)) {
////					state = SessionState.Initial;
////				} else {
////					logger.error("State transition Not implemented yet!");
////				}
////			} else {
////				if (state == SessionState.TransmittingData) {
////					
////				}
////			}
//			
//			
//		} else {
//			// if it is a server transaction than it means we haven't receive ACK after sending 200
//		}
		
		state = SessionState.Initial;
		
		theStack.notifyTransactionTimeout(transaction);
		
	}

	private class SessionTimeoutTask implements Runnable {

		@Override
		public void run() {
			theStack.notifySessionTimeout(CftSession.this);
			}
		}

	/**
	 * invalidate the session
	 */
	public void invalidate() {
		tearDown();
		

		// clear the client transactions
		for(ClientTransaction clientTx: clientTxs.values()) {
			clientTx.invalidate();
		}
		
		clientTxs.clear();
		
		
		// clear the server transactions
		for(ServerTransaction serverTx: serverTxs.values()) {
			serverTx.invalidate();
		}
		
		serverTxs.clear();
		
		// remove itself from the stack;
		theStack.removeSession(sessionID);
		
	}

	public void setResourceName(String resourceName) {
		this.resourceName = resourceName;
	}

	public void setLastRequestSeqNumber(long sequenceNumber) {
		this.peerSequenceNumber = sequenceNumber;
		
	}
	
	public String getResourceName() {
		return this.resourceName;
	}
	
	public long getLastRequestSeqNumber() {
		return peerSequenceNumber;
		
	}	
	/**
	 * Check if the THANKS request is valid within the context of this session
	 * @param thanksRequest
	 * @return
	 */
	public boolean isValidThanks(CftRequest thanksRequest) {
		
		if (!thanksRequest.getResourceName().equals(this.resourceName)) {
			logger.error("invalid resource name in THANKS");
			return false;
		}
		
		if (thanksRequest.getSequenceNumber() != this.getLastRequestSeqNumber() + 1) {
			logger.error("invalid sequence number in THANKS");
			return false;
		}
		
		// check the chunkRange was valid
		String chunkRangeHdr = thanksRequest.getHeader(Header.HN_ChunkRange);
		if (null == chunkRangeHdr) {
			logger.error("invalid chunk range in THANKS");
			
			return false;
		}

		// check whether the chunk range was correct
		ChunkRange chunkRange = ChunkRange.parseChunkRangeHeader(chunkRangeHdr);	
		
		if (chunkRange.equals(this.getChunkRange())) {
			logger.error("invalid chunk range in THANKS");
			
			return false;
		}
		
	
		// check the number of Chunks is within range
		String numberOfChunksStr = thanksRequest.getHeader(Header.HN_NumberOfChunks);
		if (null == numberOfChunksStr) {
			logger.error("no NumberOfChunks header found in THANKS");
			return false;
		}
		
		long numberOfChunksInHeader = Long.parseLong(numberOfChunksStr);
		if (numberOfChunksInHeader > chunkRange.getTotalNumberOfChunks()) {
			logger.error("invalid NumberOfChunks header found in THANKS");
			return false;
			
		}
		
		return true;
	}

	private ChunkRange getChunkRange() {

		return this.chunkRange;
	}
	
	public void setChunkRange(ChunkRange chunkRange) {
		this.chunkRange = chunkRange;
	}

}