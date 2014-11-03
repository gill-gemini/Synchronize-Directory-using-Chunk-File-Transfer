import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.BitSet;
import java.util.concurrent.CountDownLatch;

import org.apache.log4j.Logger;

import stack.CftRequest;
import stack.CftResponse;
import stack.CftSession;
import stack.ClientTransaction;
import stack.DirInfo;
import stack.Header;
import stack.Utils;

/**
 * The state machine on the client of the SYNC
 * @author shaohong
 *
 */
public class SyncClientSession {
	private static Logger logger = Logger.getLogger(SyncClientSession.class);

	public enum SyncClientSessionState {Initial, ReceivingData, AllDataReceived};
	
	CftSession session;
	String dirName;
	long lastSyncTimeStamp;
	long peerTimeStamp;
	CountDownLatch latch;
	
	SyncClientSessionState state = SyncClientSessionState.Initial;
	
	byte[][] packetBuffer ; 
	BitSet packetsReceived;	
	
	DirInfo retrievedDirInfo;
	
	public SyncClientSession(CftSession session, String dirName,
			long timeStamp, CountDownLatch latch) {
		this.session = session;
		this.dirName = dirName;
		this.lastSyncTimeStamp = timeStamp;
		this.latch = latch;
		
		state = SyncClientSessionState.Initial;
	}

	
	public void initiateSession() {
		ClientTransaction clientTx = session.createClientTransaction();
		CftRequest request = clientTx.createRequest(CftRequest.SYNC);
		
		// add directory name
		request.setResourceName(dirName);
		
		// add last sync timestamp header
		request.setHeader(Header.HN_LastSyncStamp, Long.toString(lastSyncTimeStamp));
		
		logger.debug("sending " +request.getRequestLine());
		try {
			clientTx.sendRequest(request);
			state = SyncClientSessionState.ReceivingData;
		} catch (IOException e) {
			logger.error("Failed to send request!", e);

			tearDown();
		}
		
	}

	
	public DirInfo getReceivedDirInfo(){
		
		return retrievedDirInfo;
		
	}

	// return the timestamp information received from peer
	public long getPeerTimeStamp() {
		return peerTimeStamp;
	}
	
	private void tearDown() {
		latch.countDown();
		
	}


	public void processSyncResponse(CftResponse response) {
		String pnHeader = response.getHeader(Header.HN_PN);
		String[] strings = pnHeader.split("/");
		int totalPackets = Integer.parseInt(strings[1]);
		int currPacketNumber = Integer.parseInt(strings[0]);

		synchronized (this) {
			if (null == packetsReceived) {
				packetsReceived = new BitSet(totalPackets);

				packetBuffer = new byte[totalPackets][];
			}
		}

		int bufferIdx = currPacketNumber - 1;

		packetBuffer[bufferIdx] = response.getContent();
		
		// mark received and send sync_ack
		packetsReceived.set(bufferIdx);
		
		if (allDataReceived()){
			logger.info("all drectory info received");
			
			ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
			for(int i=0; i<packetBuffer.length; i++){
				try {
					byteStream.write(packetBuffer[i]);
				} catch (IOException e) {
					logger.error(e);
				}
			}
			
			String xmlStr;
			try {
				xmlStr = byteStream.toString("UTF-8");
				retrievedDirInfo = Utils.getDirInfoFromXmlStr(xmlStr);
				
			} catch (Exception e) {
				logger.error(e);
			}
			
			tearDown();
		}
		
		
	}


	private boolean allDataReceived() {
		return (packetsReceived.cardinality() ==  packetBuffer.length);
	}


	public void processTransactionTimeout(ClientTransaction clientTx) {
		logger.debug("Transaction timed out for :\n"
				+ clientTx.getRequest().getTheHeaderPart());
		tearDown();		
	}
}
