package stack;
import java.io.IOException;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.Map.Entry;


public abstract class CftMessage {

	protected Hashtable<String, String> headers = new Hashtable<String, String>();
	private byte[] content;
	
	private String method;
	private String sessionID = null;
	protected CftTransaction transaction = null;
	
	String targetHost;

	int targetPort;
	
	public void setHeader(String headerName, String headerValue) {
		headers.put(headerName, headerValue);
	}
	
	public String getHeader(String headerName) {
		return headers.get(headerName);
	}
	
	public boolean hasHeader(String headerName) {
		return headers.containsKey(headerName);
	}
	
	
	public void setContent(byte[] content) {
		// make a copy of the content so that any later change on the referenced byte array doesn't have an impact
		this.content = Arrays.copyOf(content, content.length);
	}
	
	public byte[] getContent() {
		return this.content;
	}
	
	public abstract boolean isRequest();

	public abstract boolean isResponse();

	/**
	 * get the method associated with this message. 
	 * @return
	 */
	public String getMethod() {
		if (method == null) {
			String cSeqStr = getHeader(Header.HN_CSeq);
			String[] strings = cSeqStr.split(" ");
			if (strings != null) {
				method = strings[1];
			}
			
		}
		
		return method;
	}
	
	public void setMethod(String method) {
		this.method = method;
	}
	
	public String getSessionID() {
		if (sessionID == null) {
			// see if we have the header ready
			sessionID = getHeader(Header.HN_SessionID);
		}
		return sessionID;
	}

	public void setSessionID(String sessionID) {
		this.sessionID = sessionID;
	}
	
	
	/**
	 * get the request/status line and the header parts of the message
	 * @return
	 */
	public String getTheHeaderPart() {
		StringBuilder sb = new StringBuilder();

		// synchronize the headers with the object states
		syncHeaders();

		// the request line
		if (isRequest()){
			sb.append(getRequestLine()).append("\r\n");
		} else {
			sb.append(getStatusLine()).append("\r\n");
		}

		// all the headers
		for (Entry<String, String> header : headers.entrySet()) {
			sb.append(header.getKey() + ": " + header.getValue()).append("\r\n");
		}

		sb.append("\r\n");
		
		return sb.toString();
	}
	/**
	 * 
	 * @return the serialization form of the message for transmission
	 */
	public byte[] getSerilizationForm() throws IOException {

		String headerPart = getTheHeaderPart();
		
		byte[] headerBytes = headerPart.getBytes("UTF-8");

		if (null != getContent()) {
			int newSize = headerBytes.length + getContent().length;

			byte[] msgBytes = Arrays.copyOf(headerBytes, newSize);

			System.arraycopy(getContent(), 0, msgBytes, headerBytes.length,
					getContent().length);

			return msgBytes;

		} else {

			return headerBytes;
		}

		
	}
	

	
	public abstract String getStatusLine();

	public abstract String getRequestLine();
	
	

	protected void syncHeaders() {
		// use the state to update headers
		setHeader(Header.HN_SessionID, getSessionID());
	}

	public String getTargetHost() {
		return targetHost;
	}

	public void setTargetHost(String targetHost) {
		this.targetHost = targetHost;
	}

	public int getTargetPort() {
		return targetPort;
	}

	public void setTargetPort(int targetPort) {
		this.targetPort = targetPort;
	}
		
	/**
	 * Get the transaction associated with this message
	 * @return
	 */
	public CftTransaction getTransaction() {
		return transaction;
	}
	
	public void setTransaction(CftTransaction transaction) {
		this.transaction = transaction;
	}
		
}
