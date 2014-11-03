package stack;


import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.log4j.Logger;

public class SyncCFTStack implements Runnable {
	public static final int DefaultListeningPort = 7000;
	
	public static boolean RobustnessEnhanced = false;
	
	private static Logger logger = Logger.getLogger(SyncCFTStack.class);

	private int listeningPort;

	private DatagramSocket serverSocket;

	private Hashtable<String, CftSession> sessions = new Hashtable<String, CftSession>();

	private Thread stackThread = null;

	private volatile boolean shutDownStack = false;

	private List<StackUser> stackUsers = new ArrayList<StackUser>();

	// number of threads executing timer tasks
	private static final int NUM_TIMER_THREADS = 5;
	
	// the scheduler to run timer tasks, i.e. retransmission timer;
	public static ScheduledExecutorService scheduler;
	
	// the executor service to handle incoming packets
    private final ExecutorService pool;

	LossModel lossModel; // the lossModel used by this stack;

	
	public LossModel getLossModel() {
		return lossModel;
	}



	public void setLossModel(LossModel lossModel) {
		this.lossModel = lossModel;
	}

	public Thread getStackThread() {
		return stackThread;
	}
	


	public CftSession getSession(String sessionID) {
		return sessions.get(sessionID);
	}

	

	public SyncCFTStack(int listeningPort) throws Exception {
		
		super();
		this.listeningPort = listeningPort;
		serverSocket = new DatagramSocket(listeningPort);
		serverSocket.setReceiveBufferSize(1500*200);
		serverSocket.setSoTimeout(2000);
	    scheduler = Executors.newScheduledThreadPool(NUM_TIMER_THREADS);
	    
	    pool = Executors.newFixedThreadPool(5);
	    
		// set logging level to be same as root logger
		logger.setLevel(Logger.getRootLogger().getLevel());
		System.out.println("SyncCFTStack setting log level to :" + logger.getLevel());
	}

	public void shutDown(){
		shutDownStack = true;
		serverSocket.close();
		scheduler.shutdownNow();
		pool.shutdownNow();
	}

	@Override
	public void run() {
		byte[] receiveData = new byte[1500];
		while (true) {
			DatagramPacket receivePacket = new DatagramPacket(receiveData,
					receiveData.length);

			try {
				
				serverSocket.receive(receivePacket);
				
			} catch (SocketTimeoutException e) {
				// check if the thread has been asked to stop
				if (shutDownStack) {
					
					// close the socket
					serverSocket.close();
					break;
				} else {
					continue;
				}
			} catch (IOException e) {
				if (!shutDownStack) {
					logger.error("meet IOException", e);
				}
				break;
			}
			
			// handling the incoming packets. preferably in a separate thread
			handleIncomingPacket(receivePacket);			

		}

		logger.info("stack stopped!");
	}
	
	/**
	 * handle the incoming packet
	 */
	private void handleIncomingPacket(DatagramPacket receivePacket) {
		byte[] receivedBytes = Arrays.copyOfRange(receivePacket.getData(),
				receivePacket.getOffset(), receivePacket.getOffset()
						+ receivePacket.getLength());
		
		InetAddress address = receivePacket.getAddress();
		int port = receivePacket.getPort();
		
		PacketHandler packetHandler = new PacketHandler(address, port, receivedBytes);
//		packetHandler.run();
		pool.execute(new PacketHandler(address, port, receivedBytes));
	}

	/**
	 * notify stackUsers of the received message
	 * @param receivedMsg
	 */
	private void notifyStackUsers(CftMessage receivedMsg) {
		for(StackUser stackUser: stackUsers){
			if (receivedMsg.isRequest()){
				stackUser.processRequest((CftRequest) receivedMsg);
			} else {
				stackUser.processResponse((CftResponse) receivedMsg);
			}
		}
	}


	CftMessage parseMessage(byte[] msgBytes) throws Exception {
		// first, find out the header and content part, which are separated by
		// CRLFCRLF
		int currPos = 0;
		int contentStartPos = -1;

		CftMessage cftMessage = null;
		byte[] headerBytes;
		while (currPos < msgBytes.length) {
			if (msgBytes[currPos] == 13) {
				if ((currPos + 3) < msgBytes.length) {
					if ((msgBytes[currPos + 1] == 10)
							&& (msgBytes[currPos + 2] == 13)
							&& (msgBytes[currPos + 3] == 10)) {
						contentStartPos = currPos + 4;
						break;
					}
				}
			}

			currPos++;
			continue;
		}

		if (-1 == contentStartPos) {
//			logger.error("sorry, couldn't parse this meessage" );
			logger.error("No contentStartPos found");			
			return null;
		}
		headerBytes = Arrays.copyOfRange(msgBytes, 0, contentStartPos - 2);
		ByteArrayInputStream inputStream = new ByteArrayInputStream(headerBytes);
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				inputStream, "UTF-8"));

		String line = reader.readLine();

		String firstPart;
		String secondPart;
		
		// parse the first line
		int firstSpaceIndex = line.indexOf(' ');
		if (firstSpaceIndex == -1) {
			logger.error("No space found in the first line");
			throw new IllegalArgumentException("Stack cann't pase the message");			
		} else {
			firstPart = line.substring(0, firstSpaceIndex);
			secondPart = line.substring(firstSpaceIndex+1);
		}

		// check if this message is a request or a response
		if (Utils.isInteger(firstPart)) {
			cftMessage = new CftResponse(Integer.parseInt(firstPart), secondPart);
		} else {
			cftMessage = new CftRequest(null, firstPart);
			((CftRequest) cftMessage).setResourceName(secondPart);
		}

		// parse the headers
		line = reader.readLine();
		while ((line != null) && (!line.isEmpty())) {
			String[] parts = line.split(": ");

			cftMessage.setHeader(parts[0], parts[1]);
			line = reader.readLine();

		}

		// set the content
		if ((contentStartPos != -1) && (contentStartPos != msgBytes.length)) {
			byte[] contentBytes = Arrays.copyOfRange(msgBytes, contentStartPos,
					msgBytes.length);
			cftMessage.setContent(contentBytes);
		}

		// // associate it with the transaction and session
		// String sessionID = cftMessage.getSessionID();
		//
		// CftSession session = getSession(sessionID);
		//
		// if (cftMessage.isRequest()){
		// // session.ServerTransaction();
		// } else {
		// // session.getClientTransaction();
		// }

		// stack can reject a request if it is referring to a non-existing
		// session and
		// it is not a session setup commands, such as RETRIEVE
		return cftMessage;
	}

	/**
	 * start the stack. It will listen and prepare to receive traffic
	 */
	public void startRunning() {
		if (null != stackThread) {
			throw new IllegalStateException("The stack is already started!");
		}

		stackThread = new Thread(this);
		stackThread.start();

		logger.info("SyncCFT stack stared, listening on port " + listeningPort);


	}

	public void registerStackUser(StackUser stackUser) {

		stackUsers.add(stackUser);
	
	}



	public CftSession createSession() {
		
		UUID uuid = UUID.randomUUID();
		String randomUUIDString = uuid.toString();		

		CftSession newSession = new CftSession(randomUUIDString);
		
		// associate the session with the stack
		newSession.setTheStack(this);
		
		// save the session in stack;
		sessions.put(newSession.getSessionID(), newSession);
		
		return newSession;
	}
	
	public CftSession createSession(String sessionID) {

		CftSession newSession = new CftSession(sessionID);
		
		// associate the session with the stack
		newSession.setTheStack(this);
		
		// save the session in stack;
		sessions.put(newSession.getSessionID(), newSession);
		
		return newSession;
	}
	
	
	public void removeSession(String sessionID) {
		sessions.remove(sessionID);	
	}


	public void sendMessage(CftMessage cftMsg) throws IOException {
		logger.trace("send message: \n" + cftMsg.getTheHeaderPart());
		
		if ((getLossModel() != null) && (LossModel.LossState.Loss == getLossModel().getState())) {
			logger.error("packet is lost according to our model!");
			return;
		}
		
		String sessionID = cftMsg.getSessionID();
		CftSession session = sessions.get(sessionID);
		
		// Get the internet address of the specified host
		InetAddress address = InetAddress.getByName(session.getPeerHost());
		int port = session.getPeerPort();
		if (0 == port) {
			throw new IllegalArgumentException("port = 0!");
		}
		
		// Initialize a datagram packet with data and address
		byte[] message = cftMsg.getSerilizationForm();
		
		DatagramPacket packet = new DatagramPacket(message, message.length, address, port);
		
		// send the packet through serverSocket.
		serverSocket.send(packet);
	
	}



	/**
	 * notify stack users that the given clientTx has timed out
	 * @param transaction
	 */
	public void notifyTransactionTimeout(CftTransaction transaction) {
		for(StackUser stackUser: stackUsers){
				stackUser.processTransactionTimeout(transaction);
		}		
	}



	public void notifySessionTimeout(CftSession cftSession) {
		for(StackUser stackUser: stackUsers){
			stackUser.processSessionTimeout(cftSession);
		}			
	}

	
	class PacketHandler implements Runnable {
		private byte[] receivedBytes;
		private InetAddress peerAddress;
		private int peerPort;

		PacketHandler(InetAddress peerAddress, int port, byte[] receivedBytes) {
			this.receivedBytes = receivedBytes;
			this.peerAddress = peerAddress;
			this.peerPort = port;
		}

		public void run() {
			
			// handle the incoming packet

			CftMessage receivedMsg;
			try {
				receivedMsg = parseMessage(receivedBytes);

				if (null != receivedMsg) {
					logger.trace("received from "
							+ peerAddress.getHostAddress() + ":" + peerPort
							+ "\n" + receivedMsg.getTheHeaderPart());

				} else {
					logger.error("receivedBytes.length : " + receivedBytes.length);
					logger.error("coudln't parse the message from "
							+ peerAddress.getHostAddress() + ":" + peerPort + "\n" + new String(receivedBytes));
//					logger.trace("weird message is: " + new String(receivedBytes));
					return;
				}

				// update the session states with this message
				String sessionID = receivedMsg.getSessionID();
				CftSession session = getSession(sessionID);
				
				if (session == null) {
					if (receivedMsg.isRequest()) {

						// we will not have a session if this is a session
						// create request, such as RETRIEVE
						session = createSession(sessionID);

						// set the peer Host/port info
						session.setPeerHost(peerAddress.getHostAddress());
						session.setPeerPort(peerPort);
						
						if (SyncCFTStack.RobustnessEnhanced) {
							// save the resource name and command sequence
							CftRequest request = (CftRequest) receivedMsg;

							// save the resource name and sequence number for later reference checking
							session.setResourceName(request.getResourceName());
//							
//							if (request.getMethod().equals(CftRequest.RETRIEVE)) {
//								session.setLastRequestSeqNumber(request.getSequenceNumber());
//							}
						}
					} else {
						logger.error("Drop packets since no session to handle :"
								+ receivedMsg.getTheHeaderPart());
						return;
					}
				} else {
					
					if (SyncCFTStack.RobustnessEnhanced) {

						// Here, we check whether the message comes from the
						// valid peer
						if (!peerAddress.getHostAddress().equals(
								session.getPeerHost())) {
							logger.error("peer ip address is not within the session context! Drop this packet.");
							return;
						}

						if (peerPort != session.getPeerPort()) {
							logger.error("peer port number is not within the session context! Drop this packet.");
							return;
						}
					}
				}
				
				// notify the session, so that it can change states
				session.processMessage(receivedMsg);

				//TODO for retransmission of request, we don't need to notify the stack user again.
				
				if (receivedMsg.isRequest()) {
					CftRequest cftRequest = (CftRequest) receivedMsg;
					if (cftRequest.isRetransmittedRequest()) {
						return;	
					}
					
				}
				
				// let stack users handle the message
				notifyStackUsers(receivedMsg);
				
				
			} catch (Exception e) {
				logger.error("meet exception", e);
				e.printStackTrace();
			}

			
		}
	}	
}
