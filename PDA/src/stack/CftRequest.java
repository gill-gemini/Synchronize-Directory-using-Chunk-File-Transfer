package stack;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map.Entry;


public class CftRequest extends CftMessage {

	public static final String SYNC = "SYNC";
	public static final String SYNC_ACK = "SYNC_ACK";	
	public static final String RETRIEVE = "RETRIEVE";
	public static final String DELIVER = "DELIVER";
	public static final String THANKS = "THANKS";
	public static final String ACK = "ACK";
	
	String resourceName;	// the resourceName is a name that identifies a directory or a file.

	boolean retransmittedRequest = false;
	
	public CftRequest(CftTransaction transaction, String method) {
		super();
		this.transaction = transaction;
		this.setMethod(method);
	}

	@Override
	public byte[] getSerilizationForm() throws IOException {
		StringBuilder sb = new StringBuilder();

		// synchronize the headers with the object states
		syncHeaders();

		// the request line
		sb.append(this.getMethod() + " " + getResourceName()).append("\r\n");

		// all the headers
		for (Entry<String, String> header : headers.entrySet()) {
			sb.append(header.getKey() + ": " + header.getValue()).append("\r\n");
		}

		sb.append("\r\n");

		byte[] headerBytes = sb.toString().getBytes("UTF-8");

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

	@Override
	public boolean isRequest() {
		return true;
	}

	@Override
	public boolean isResponse() {
		return false;
	}


	
	/**
	 * Create a response based on this request
	 * @param reasonPhrase 
	 * @param statusCode 
	 * @return
	 */
	public CftResponse createResponse(int statusCode, String reasonPhrase) {
		CftResponse newResponse = new CftResponse(statusCode, reasonPhrase);
		newResponse.setMethod(this.getMethod());
		newResponse.setSessionID(this.getSessionID());
		newResponse.setHeader(Header.HN_CSeq, this.getHeader(Header.HN_CSeq));
		
		return newResponse;
	}


	
	
	public String getResourceName() {
		return resourceName;
	}

	public void setResourceName(String resourceName) {
		this.resourceName = resourceName;
	}


	@Override
	public String getStatusLine() {
		return null;
	}

	@Override
	public String getRequestLine() {
		return getMethod() + " " + getResourceName();
	}

	public boolean isRetransmittedRequest() {
		return retransmittedRequest;
	}

	public void setRetransmittedRequest(boolean retransmittedRequest) {
		this.retransmittedRequest = retransmittedRequest;
	}

	public long getSequenceNumber() {
		String cseqStr = getHeader(Header.HN_CSeq);
		String[] strings = cseqStr.split(" ");
		return Long.parseLong(strings[0]);
	}	

}
