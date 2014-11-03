package stack;

import java.util.Map.Entry;

public class CftResponse extends CftMessage {

	private int statusCode;

	private String statusPhrase;
	
	public CftResponse(int statusCode, String status) {
		super();
		this.statusCode = statusCode;
		this.statusPhrase = status;
	}
	
	@Override
	public boolean isRequest() {
		return false;
	}

	@Override
	public boolean isResponse() {
		return true;
	}

	public int getStatusCode() {
		return statusCode;
	}

	public String getStatus() {
		return statusPhrase;
	}

	public boolean isSuccessfulResponse() {
		return (getStatusCode() == 200);
	}

	@Override
	public String getRequestLine() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String getStatusLine() {
		return getStatusCode() + " " + getStatus();
	}	
}
