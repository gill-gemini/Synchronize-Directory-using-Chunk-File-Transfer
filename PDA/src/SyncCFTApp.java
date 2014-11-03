import jargs.gnu.CmdLineParser;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.Logger;

import stack.DirInfo;
import stack.FileInfo;
import stack.LossModel;
import stack.SyncCFTStack;


public class SyncCFTApp {

	private static Logger logger = Logger.getLogger(SyncCFTApp.class);
	
	public static long SYNC_INTERVAL = 20;	// we'll sync with peer every 20 seconds

	// the scheduler to run timer tasks, i.e. retransmission timer;
	public ScheduledExecutorService scheduler;
	
	public String localDir;
	public int serverListeningPort;
	public Hop peerHop = null;
	LossModel lossModel = null;
	
	public SyncCFTApp() {
	    scheduler = Executors.newScheduledThreadPool(2);
		
	}

	public void action() throws Exception {
		PeerSyncTask peerSyncTask = null;
		
		// schedule the client application
		if (peerHop!=null) {
			peerSyncTask = new PeerSyncTask();

			long delay = SYNC_INTERVAL + Math.round(Math.random() *  SYNC_INTERVAL); 
			System.out.println("**** will update with peer after " + delay + " seconds");
			scheduler.schedule(peerSyncTask, SYNC_INTERVAL, TimeUnit.SECONDS);
		}
		
		// start the server
		SyncCFTImpl syncCFT = new SyncCFTImpl(localDir, serverListeningPort);

		syncCFT.setLossModel(lossModel);
		
		syncCFT.runAsServer();
		
		scheduler.shutdownNow();
		
		if (null != peerSyncTask) {
			peerSyncTask.tearDown();			
		}


		
	}
	
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		
		SyncCFTApp syncCFTApp = new SyncCFTApp();
		
		// read some command line parameters
		CmdLineParser parser = new CmdLineParser();
		CmdLineParser.Option portOption = parser.addIntegerOption('l', "localport");
		CmdLineParser.Option localDirOption = parser.addStringOption('d', "dir");
		
		CmdLineParser.Option loss2LossProbOption = parser.addDoubleOption('q', "loss2LossProb");
		CmdLineParser.Option noLoss2LossProbOption = parser.addDoubleOption('p', "noLoss2LossProb");
		
		// robustness enhancement option
		CmdLineParser.Option robustnessOption = parser.addBooleanOption('r', "robust");
		
		// peer connection option
		CmdLineParser.Option peerOption = parser.addStringOption('c', "peerConnection");
		
		try {
			parser.parse(args);

			// robustness enhancement
	        Boolean robust = (Boolean)parser.getOptionValue(robustnessOption);
	        if (robust == Boolean.TRUE) {
	        	SyncCFTStack.RobustnessEnhanced = true;
	        	logger.info("running robustness enhanced version of the STACK!");
	        } else {
	        	SyncCFTStack.RobustnessEnhanced = false;
	        }
			
			Integer listeningPort = (Integer) parser.getOptionValue(portOption);

			String localDir = (String) parser.getOptionValue(localDirOption);

			double p=0, q=0; // by default no loss
			
			Double loss2LossProb = (Double) parser.getOptionValue(loss2LossProbOption);
			Double noLoss2LossProb = (Double) parser.getOptionValue(noLoss2LossProbOption);

			if ((loss2LossProb == null) && (noLoss2LossProb == null)) {
				// just use default values in this case
			} else {
				if (null != loss2LossProb) {
					q = loss2LossProb.doubleValue();
					if (null == noLoss2LossProb) {
						p = q;
					} else {
						p = noLoss2LossProb.doubleValue();
					}
				} else {
					p = noLoss2LossProb.doubleValue();
					q = p;
				}
			}
			
			// create the loss model;
			LossModel lossModel = new LossModel(q, p);
			logger.info("LossModel is: " + lossModel.toString());
			
			// Check whether peer option was specified
			Hop peerHop = null;
			Object optionValue = parser.getOptionValue(peerOption);
			if (null == optionValue) {
				logger.info("peer connection was not specified");
			} else {
				String peerConnectionString = (String) optionValue;
				peerHop = Hop.parseHopInfo(peerConnectionString);
			}
			
			
			syncCFTApp.localDir = localDir;
			syncCFTApp.serverListeningPort = listeningPort;
			syncCFTApp.peerHop = peerHop;
			syncCFTApp.lossModel = lossModel;
			
			
			syncCFTApp.action();
			

		} catch (CmdLineParser.OptionException e) {
			System.err.println(e.getMessage());
			logger.error("command line error ", e);
			
			printUsage();
			System.exit(2);
		}

	
	}

	
	private static void printUsage() {
		System.err
				.println("Usage: SyncCFT {-l,--localport} portNumber {-d, --dir} syncDir"
						+ " {-p, --noLoss2LossProb} Loss2LossProbability {-q, --noLoss2LossProb} NoLoss2LossProbability "
						+ " {-c, --peerConnection} peerIP:port");
	}		
	
	
	private class PeerSyncTask implements Runnable {
		int localPort = 5555;

		long lastSyncTimeStamp = 0;
		
		SyncCFTImpl syncCFT = new SyncCFTImpl(localDir, localPort);

		private ScheduledFuture<?> scheduledFuture;

		public PeerSyncTask() throws Exception {

			syncCFT.initialize();			
		}

		public void tearDown() {
			syncCFT.cleanUp();
			
			scheduledFuture.cancel(true);
		}
		
		@Override
		public void run() {
			try {
				System.out.println("automatic updating from peer :" + peerHop.getPeerHost()+":"+peerHop.getPeerPort());
				
				logger.info("automatic updating from peer :" + peerHop.getPeerHost()+":"+peerHop.getPeerPort());

				updateFromPeer();
				
			} catch (Exception e) {
				logger.error("meet error when updating from peer", e);
			}

			long delay = SYNC_INTERVAL + Math.round(Math.random() *  SYNC_INTERVAL); 

			
			logger.info("***** will update with peer "
					+ peerHop.getPeerHost() + ":" + peerHop.getPeerPort()
					+ " after " + delay + " seconds");
			
			// schedule another run;
			scheduledFuture = scheduler.schedule(this, delay, TimeUnit.SECONDS);
		}

		private void updateFromPeer() throws Exception {

			logger.info("getting remote directory info");

			DirInfo remoteDirInfo = syncCFT.getRemoteDirInfo(
					peerHop.getPeerHost(), peerHop.getPeerPort(), "SharedDir",
					lastSyncTimeStamp);

			//TODO ideally this value should come from peer instead of locally generated
			lastSyncTimeStamp = System.currentTimeMillis();
			
			if (null == remoteDirInfo) {
				logger.error("failed to get remote directory Info!");
				return;
			}

			DirectoryInfoManager dirManager = new DirManagerImpl(localDir);
			DirInfo localDirInfo = dirManager.getLocalDirInfo();

			List<FileInfo> files2bUpdated = new ArrayList<FileInfo>();
			for (FileInfo remoteFile : remoteDirInfo.getFiles()) {
				// get the file that are of the same name, and have a newer
				// timestamp on the remote site
				// also get the file that only exists on the remote site

				if (!localDirInfo.hasFile(remoteFile)) {
					if (remoteFile.modifcationType != FileInfo.ModificationType.DELETE) {
						// logger.info("need to retrieve " + remoteFile.name);
						files2bUpdated.add(remoteFile);
					}
				} else {
					FileInfo localFile = localDirInfo
							.getFileInfo(remoteFile.name);
					if (remoteFile.modificationTime > localFile.modificationTime) {
						// logger.info("need to retrieve " + remoteFile.name);
						files2bUpdated.add(remoteFile);
					}
				}

			}

			logger.info("The following files needs to be updated according to remote peer\n" + files2bUpdated);
			
			for (FileInfo file2bUpdate : files2bUpdated) {
				System.out.println(file2bUpdate.toString());
			}

			// now update local directory accordingly
			for (FileInfo file2bUpdate : files2bUpdated) {
				if (file2bUpdate.modifcationType == FileInfo.ModificationType.DELETE) {
					logger.info("delete file: " + file2bUpdate.name);

					dirManager.deleteFileFromLocalDirectory(file2bUpdate.name, file2bUpdate.modificationTime);

				} else {
					try {
						logger.info("getting file " + file2bUpdate
								+ " from remote");

						// download the new file and update local meta file
						syncCFT.getRemoteFile(peerHop.getPeerHost(),
								peerHop.getPeerPort(), file2bUpdate.name,
								file2bUpdate.size,
								file2bUpdate.modificationTime);

						dirManager.addFileInfo(file2bUpdate);
					} catch (Exception e) {
						logger.error("meet exception getting remote file "
								+ file2bUpdate, e);
					}
				}

			}

		} // end of UPDATE
		
		

	}

	
}
