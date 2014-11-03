import jargs.gnu.CmdLineParser;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import stack.LossModel;
import stack.SyncCFTStack;


/**
 * The syncCFT server program which starts the protocol stack and listen for incoming requests
 * @author shaohong
 *
 */
public class SyncCFTServerApp {

	private static Logger logger = Logger.getLogger(SyncCFTServerApp.class);
			
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {

		Level level = Logger.getRootLogger().getLevel();
		
		// read some command line parameters
		CmdLineParser parser = new CmdLineParser();
		CmdLineParser.Option portOption = parser.addIntegerOption('l', "localport");
		CmdLineParser.Option localDirOption = parser.addStringOption('d', "dir");
		
		CmdLineParser.Option loss2LossProbOption = parser.addDoubleOption('q', "loss2LossProb");
		CmdLineParser.Option noLoss2LossProbOption = parser.addDoubleOption('p', "noLoss2LossProb");
		
		//logging level option
		CmdLineParser.Option loglevelOption = parser.addStringOption('z', "loggingLevel");	
		
		// robustness enhancement option
		CmdLineParser.Option robustnessOption = parser.addBooleanOption('r', "robust");
		
		try {
			parser.parse(args);

			String loggingLevel = (String) parser.getOptionValue(loglevelOption);
			level = Level.toLevel(loggingLevel, level);
			Logger.getRootLogger().setLevel(level);
			
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
			
			SyncCFTImpl syncCFT = new SyncCFTImpl(localDir,
					listeningPort.intValue());

			syncCFT.setLossModel(lossModel);
			
			syncCFT.runAsServer();

		} catch (CmdLineParser.OptionException e) {
			System.err.println(e.getMessage());
			logger.error("command line error ", e);
			
			printUsage();
			System.exit(2);
		}

	}

	private static void printUsage() {
		System.err.println("Usage: SyncCFTServerApp {-l,--localport} portNumber {-d, --dir} syncDir" +
				"{-p, --noLoss2LossProb} Loss2LossProbability {-p, --noLoss2LossProb} NoLoss2LossProbability");
	}
}
