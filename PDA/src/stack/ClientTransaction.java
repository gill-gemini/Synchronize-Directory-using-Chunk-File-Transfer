package stack;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

public class ClientTransaction extends CftTransaction {

	private static Logger logger = Logger.getLogger(ClientTransaction.class);

	enum ClientTxState {
		Initial, RequestSent, TxTimeout, ResponseReceived
	};

	CftRequest request = null;
	
	CftRequest ackRequest = null;

	ClientTxState state;

	public CftRequest getRequest() {
		return request;
	}

	public ClientTransaction() {
		super();

		state = ClientTxState.Initial;
	}

	/**
	 * Create a CftRequest
	 * 
	 * @param requestMethod
	 * @return
	 */
	public CftRequest createRequest(String requestMethod) {

		if (request != null) {
			throw new IllegalStateException("request already exists!");
		}

		request = new CftRequest(this, requestMethod);
		request.setSessionID(this.getSession().getSessionID());

		int cseq = getSession().getAndIncrementCSeq();

		String cseqHdrVal = Header.createCseqHdrValue(cseq, requestMethod);
		request.setHeader(Header.HN_CSeq, cseqHdrVal);

		return request;
	}

	/**
	 * process response in this client transaction
	 * 
	 * @param response
	 */
	public void processResponse(CftResponse response) {
		state = ClientTxState.ResponseReceived;

		// cancel the timer tasks;
		retransmissionTask.cancel(true);
		
		if (response.getMethod().equals(CftRequest.RETRIEVE)){
			// if this is response to RETRIEVE request, we'll send out the ACK for it
			// no retransmission timer needs to be started
			try {
				sendACK();
			} catch (IOException e) {
				logger.error("failed to send ACK", e);
			}
		}
	
		if (response.getMethod().equals(CftRequest.SYNC)){
			// if this is response to RETRIEVE request, we'll send out the ACK for it
			// no retransmission timer needs to be started
			try {
				sendSyncACK(response);
			} catch (IOException e) {
				logger.error("failed to send ACK", e);
			}
		}		
 
	}

	private void sendSyncACK(CftResponse response) throws IOException {
		CftRequest syncAckRequest = new CftRequest(this, CftRequest.SYNC_ACK);
		syncAckRequest.setSessionID(this.getSession().getSessionID());
		
		syncAckRequest.setResourceName(request.getResourceName());
		
		// SYNC_ACK shall reuse the CSeq of the RETRIEVE request
		syncAckRequest.setHeader(Header.HN_CSeq, response.getHeader(Header.HN_CSeq));
		
		// also copy the PN header
		syncAckRequest.setHeader(Header.HN_PN, response.getHeader(Header.HN_PN));
		
		// no need to start retransmission timers since the peer will resend the
		// response if it doesn't receive any feedback from us
		getSession().sendRequest(syncAckRequest);
	}

	public void sendACK() throws IOException {
		
		if (ackRequest == null) {
			createACK();
		}
		
		// no need to start retransmission timer for ACK request
		getSession().sendRequest(ackRequest);
		
		
	}
	
	private void createACK() {
		ackRequest = new CftRequest(this, CftRequest.ACK);
		ackRequest.setSessionID(this.getSession().getSessionID());
		
		
		ackRequest.setResourceName(request.getResourceName());
		
		// ACK shall reuse the CSeq of the RETRIEVE request
		ackRequest.setHeader(Header.HN_CSeq, request.getHeader(Header.HN_CSeq));

	}

	public void sendRequest(CftRequest request) throws IOException {

		state = ClientTxState.RequestSent;

		// save the request so that it can be retransmitted;
		this.request = request;

		getSession().sendRequest(request);

		// schedule retransmission
		retransmissionTask = SyncCFTStack.scheduler.schedule(
				new ReTransmitTimerTask(), RetransmissionTime,
				TimeUnit.MILLISECONDS);

	}

	private class ReTransmitTimerTask implements Runnable {

		private static final int MAX_RETRANMISSION_TIME = 6;
		private int retransmitCounter = 0;

		@Override
		public void run() {
			retransmitCounter ++;

			if (retransmitCounter <= MAX_RETRANMISSION_TIME) {

				// send the request again and restart another retransmission
				// task
				try {
					logger.debug("retransmitting request " + retransmitCounter
							+ " times");

					getSession().sendRequest(request);

					retransmissionTask = SyncCFTStack.scheduler.schedule(
							this, RetransmissionTime,
							TimeUnit.MILLISECONDS);

				} catch (Exception e) {
					logger.debug("failed to send request", e);
				}
			} else {
				// this transaction timeout after retransmiting max retransmission times. Let's
				// notify the transaction user
			
				getSession().notifyTransactionTimeout(ClientTransaction.this);
			}
		}

	}

	@Override
	public boolean isClientTx() {
		return true;
	}

	@Override
	public boolean isServerTx() {
		return false;
	}

	@Override
	public void invalidate() {

		if (retransmissionTask != null) {
			retransmissionTask.cancel(true);
		}
		retransmissionTask = null;
	}
}
