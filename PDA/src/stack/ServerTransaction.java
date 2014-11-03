package stack;

import java.io.IOException;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

public class ServerTransaction extends CftTransaction {

	private static Logger logger = Logger.getLogger(ServerTransaction.class);
	
	enum TransactionState {
		Initial, RequestReceived, ResponseSent
	}

	TransactionState state;
	
	CftResponse response = null;

	private ScheduledFuture<?> retransmissionTask;

	private Map<String, ScheduledFuture<?>> dirInfoRetransmissionTasks = new Hashtable<String, ScheduledFuture<?>>();
	
	public ServerTransaction() {
		super();
		state = TransactionState.Initial;

	}

	public void processRequest(CftRequest request) {
		
		if (request.getMethod().equals(CftRequest.ACK)) {
			if (retransmissionTask != null) {
				retransmissionTask.cancel(true);
			}
			return;			
		}
		
		if (request.getMethod().equals((CftRequest.SYNC_ACK))) {
			logger.debug("handling SYNC_ACK with packet number: " + request.getHeader(Header.HN_PN));
			String key = request.getHeader(Header.HN_PN);
			ScheduledFuture<?> future = dirInfoRetransmissionTasks.get(key);
			if (future != null) {
				future.cancel(true);
				logger.debug("canceling retranmission task for PN " + key + " Canceled:" + future.isCancelled());
				
			}
			return;
		}
		
		if (request.getMethod().equals((CftRequest.SYNC))) {
			// nothing todo here
		}
		
		
		if (state == TransactionState.Initial) {
			state = TransactionState.RequestReceived;
			return;
		} 
			
		if ((state == TransactionState.ResponseSent) 
					&& (!request.getMethod().equals(CftRequest.ACK)) 
					&& (!request.getMethod().equals(CftRequest.SYNC_ACK))) {
				// this is a retransmit of the original request, re-send our response
				try {
					request.setRetransmittedRequest(true);
					
					logger.debug("retransmitted request " + request.getMethod() + request.getHeader(Header.HN_ChunkID));
					
					sendResponse(this.response);
				} catch (IOException e) {
					logger.error("failed to resend response", e);
				}
			}
			
	}

	public void sendResponse(CftResponse response) throws IOException {
		
		state = TransactionState.ResponseSent;
		this.response = response;
		
		getSession().sendResponse(response);
		
		// if the response is for RETRIEVE and it is a successful response, 
		// we need to start retransmission timer  
		if (response.getMethod().equals(CftRequest.RETRIEVE) && response.isSuccessfulResponse()) {
			// schedule retransmission
			retransmissionTask = SyncCFTStack.scheduler.schedule(
					new ReTransmitTimerTask(response), RetransmissionTime,
					TimeUnit.MILLISECONDS);			
		}
		
		// for successful SYNC response, we need to start retransmission timers and only cancel them when SYNC_ACK is received
		if (response.getMethod().equals(CftRequest.SYNC)
				&& response.isSuccessfulResponse()) {
			String key = response.getHeader(Header.HN_PN);
			ScheduledFuture<?> retransmissionTask = SyncCFTStack.scheduler
					.schedule(new ReTransmitTimerTask(response),
							RetransmissionTime, TimeUnit.MILLISECONDS);
			dirInfoRetransmissionTasks.put(key, retransmissionTask);
		}
	};

	

	private class ReTransmitTimerTask implements Runnable {
		CftResponse response;
		int retransmitCounter = 0;

		private static final int MAX_RETRANMISSION_TIME = 6;

		public ReTransmitTimerTask(CftResponse response) {
			super();
			this.response = response;
		}

		@Override
		public void run() {
			retransmitCounter++;

			if (retransmitCounter <= MAX_RETRANMISSION_TIME) {
				// send the response again and restart another retransmission
				// task
				try {
					logger.debug("retransmitting response " + retransmitCounter
							+ " times");

					getSession().sendResponse(response);

					retransmissionTask = SyncCFTStack.scheduler.schedule(
							this, RetransmissionTime,
							TimeUnit.MILLISECONDS);

					// update future tasks;
					String key = response.getHeader(Header.HN_PN);
					dirInfoRetransmissionTasks.put(key, retransmissionTask);
					

				} catch (Exception e) {
					logger.debug("failed to send request", e);
				}
			} else {
				// this transaction timeout after retransmiting max retransmission times. Let's
				// notify the transaction user
			
				getSession().notifyTransactionTimeout(ServerTransaction.this);
			}
		}

	}



	@Override
	public boolean isClientTx() {
		return false;
	}

	@Override
	public boolean isServerTx() {
		return true;
	}

	@Override
	public void invalidate() {
		if (retransmissionTask != null) {
			retransmissionTask.cancel(true);
		}
		retransmissionTask = null;
		
		for(ScheduledFuture<?> futureTask : dirInfoRetransmissionTasks.values()){
			
			futureTask.cancel(true);
		}
		
		dirInfoRetransmissionTasks = null;
		
	}
	
}
