package stack;

import java.util.concurrent.ScheduledFuture;

public abstract class CftTransaction {
	
	private CftSession session;	// the session that this transaction is associated with

	private String transactionID;
	
	static int RetransmissionTime = 2000; // 2 seconds is the length of retransmission timer

	ScheduledFuture<?> retransmissionTask;

	public CftSession getSession() {
		return session;
	}

	public void setSession(CftSession session) {
		this.session = session;
	}

	public String getTransactionID() {
		return transactionID;
	}

	public void setTransactionID(String transactionID) {
		this.transactionID = transactionID;
	}
	
	public abstract boolean isClientTx();
	
	public abstract boolean isServerTx();
	
	public abstract void invalidate();
	
}
