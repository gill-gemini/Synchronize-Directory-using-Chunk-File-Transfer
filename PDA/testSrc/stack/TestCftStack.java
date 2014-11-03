package stack;
import junit.framework.Assert;

import org.junit.Before;
import org.junit.Test;

import stack.CftMessage;
import stack.CftRequest;
import stack.CftSession;
import stack.ClientTransaction;
import stack.SyncCFTStack;


public class TestCftStack {
	SyncCFTStack stack;

	@Before
	public void setup() throws Exception {
		stack = new SyncCFTStack(8080);
	}
	

	@Test
	public void testParseMessage() throws Exception{
		// first let's create a message
		CftSession session = new CftSession("some-session-id");
		ClientTransaction clientTransaction = session.createClientTransaction();
		
		CftRequest syncRequest = clientTransaction.createRequest(CftRequest.SYNC);
		syncRequest.setResourceName("./");
		
		String someContent = "1234567890";
		syncRequest.setContent(someContent.getBytes());
		
		byte[] serilizationForm = syncRequest.getSerilizationForm();
		
		// now let the stack parse it
		
		CftMessage cftMessage = stack.parseMessage(serilizationForm);
		Assert.assertTrue(cftMessage.isRequest());
		CftRequest request = (CftRequest) cftMessage;
		
		Assert.assertEquals("some-session-id", request.getSessionID());
	}
}
