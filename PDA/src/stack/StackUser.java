package stack;

/**
 * The callback interface for stack user
 * @author shaohong
 *
 */
public interface StackUser {

	/**
	 * notify the stack user that a request is received
	 * @param request
	 */
	public void processRequest(CftRequest request);
	
	/**
	 * notify the stack user that a response is received
	 * @param response
	 */
	public void processResponse(CftResponse response);
	
	
	/**
	 * notify the stack user that a transaction timeout event happened.
	 * @param message
	 */
	public void processTransactionTimeout(CftTransaction clientTx);
	
	
	/**
	 * Notify the stack user that a session has timed out.
	 * @param session
	 */
	public void processSessionTimeout(CftSession session);
	
	
}
