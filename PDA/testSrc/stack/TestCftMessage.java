package stack;
import junit.framework.Assert;

import org.junit.Test;

import stack.CftMessage;
import stack.CftRequest;
import stack.CftSession;
import stack.ClientTransaction;


public class TestCftMessage {

	@Test
	public void testSetAndGetHeader() {
		CftMessage msg = new CftRequest(null, CftRequest.RETRIEVE);
		
		String hdrName = "SomeHeader";
		String hdrVallue = "SomeValue";
		msg.setHeader(hdrName, hdrVallue);
		
		Assert.assertTrue("header doesn't exist!", msg.hasHeader(hdrName));
		
		Assert.assertEquals("header value is wrong!", hdrVallue, msg.getHeader(hdrName));
		
	}

	@Test
	public void testCreateSyncRequest() throws Exception {
		CftSession session = new CftSession("some-session-id");
		ClientTransaction clientTransaction = session.createClientTransaction();
		
		CftRequest syncRequest = clientTransaction.createRequest(CftRequest.SYNC);
		syncRequest.setResourceName("./");
		
		//check if various values are set properly
		Assert.assertEquals(session.getSessionID(), syncRequest.getSessionID());
		
		Assert.assertEquals(CftRequest.SYNC, syncRequest.getMethod());
		
		Assert.assertEquals(clientTransaction, syncRequest.getTransaction());
		
		Assert.assertNull(syncRequest.getContent());
		
		syncRequest.getHeader("CSeq");
		
		String someContent = "1234567890";
		syncRequest.setContent(someContent.getBytes());
		
		byte[] serilizationForm = syncRequest.getSerilizationForm();
		System.out.println(new String(serilizationForm));
	}
	
	
}
