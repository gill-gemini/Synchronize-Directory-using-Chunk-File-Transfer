import jargs.gnu.CmdLineParser;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import stack.DirInfo;
import stack.FileInfo;
import stack.SyncCFTStack;

/**
 * The main program of the syncCFT client
 * 
 * @author shaohong
 * 
 */
public class SyncCFTClientApp {

	public enum CommandType {
		SyncDir, GetFile, Update, unKnown
	};

	private static org.apache.log4j.Logger logger = Logger
			.getLogger(SyncCFTClientApp.class);

	public static void main(String[] args) throws Exception {
		Level level = Logger.getRootLogger().getLevel();

		RunConfig runConfig = new RunConfig();

		CmdLineParser parser = new CmdLineParser();
		CmdLineParser.Option commandOpt = parser
				.addStringOption('c', "command");

		CmdLineParser.Option peerPortOpt = parser.addIntegerOption('p', "port");
		CmdLineParser.Option peerHostOpt = parser.addStringOption('h', "host");
		CmdLineParser.Option fileOpt = parser.addStringOption('f', "file");
		CmdLineParser.Option localPortOpt = parser.addIntegerOption('l',
				"local_port");
		CmdLineParser.Option localDirOpt = parser.addStringOption('d',
				"local_dir");
		CmdLineParser.Option fileSizeOpt = parser.addLongOption('s', "size");

		//logging level option
		CmdLineParser.Option loglevelOption = parser.addStringOption('z', "loggingLevel");	
		
		try {
			parser.parse(args);

			String loggingLevel = (String) parser.getOptionValue(loglevelOption);
			level = Level.toLevel(loggingLevel, level);
			
//			System.out.println("setting log level to: " + level);
//			Logger.getRootLogger().setLevel(level);
			LogManager.getRootLogger().setLevel(level);
			
			Integer peerPort = (Integer) parser.getOptionValue(peerPortOpt);

			if (null == peerPort) {
				runConfig.peerListeningPort = SyncCFTStack.DefaultListeningPort;
			} else {
				runConfig.peerListeningPort = peerPort.intValue();
			}

			runConfig.peerHost = (String) parser.getOptionValue(peerHostOpt);

			runConfig.localListeningPort = (Integer) parser
					.getOptionValue(localPortOpt);

			runConfig.localDir = (String) parser.getOptionValue(localDirOpt);

			runConfig.command = (String) parser.getOptionValue(commandOpt);

		} catch (CmdLineParser.OptionException e) {
			System.err.println(e.getMessage());
			printUsage();
			System.exit(2);
		}

		// print the command line options
		logger.info("running config: " + runConfig);

		SyncCFTImpl syncCFT = new SyncCFTImpl(runConfig.localDir,
				runConfig.localListeningPort);

		syncCFT.initialize();

		try {

			CommandType command = CommandType.unKnown;
			try {
				command = CommandType.valueOf(runConfig.command);
			} catch (Throwable e) {
				printUsage();
				return;
			}

			if (command == CommandType.SyncDir) {
				
				logger.info("getting remote directory info");
				
				// request to sync directory
				DirInfo remoteDirInfo = syncCFT.getRemoteDirInfo(
						runConfig.peerHost, runConfig.peerListeningPort,
						"SharedDir", 0);

				if (remoteDirInfo == null) {
					logger.error("failed to get remote directory Info!");
				} else {
					for (FileInfo remoteFile : remoteDirInfo.getFiles()) {
						System.out.println(remoteFile.toString());
					}
				}

			}

			if (command == CommandType.GetFile) {
				
				runConfig.fileRequested = (String) parser.getOptionValue(fileOpt);

				runConfig.fileSize = (Long) parser.getOptionValue(fileSizeOpt);
				
				logger.info("getting remote file " + runConfig.fileRequested);
				
				// request to transfer file
				syncCFT.getRemoteFile(runConfig.peerHost,
						runConfig.peerListeningPort, runConfig.fileRequested,
						runConfig.fileSize);
			}

			if (command == CommandType.Update) {
				
				logger.info("getting remote directory info");
				
				DirInfo remoteDirInfo = syncCFT.getRemoteDirInfo(
						runConfig.peerHost, runConfig.peerListeningPort,
						"SharedDir", 0);
				if (null == remoteDirInfo) {
					logger.error("failed to get remote directory Info!");
					return;
				}
				
				DirectoryInfoManager dirManager = new DirManagerImpl(
						runConfig.localDir);
				DirInfo localDirInfo = dirManager.getLocalDirInfo();

				List<FileInfo> files2bUpdated = new ArrayList<FileInfo>();
				for (FileInfo remoteFile : remoteDirInfo.getFiles()) {
					// get the file that are of the same name, and have a newer
					// timestamp on the remote site
					// also get the file that only exists on the remote site

					if (!localDirInfo.hasFile(remoteFile)) {
						if (remoteFile.modifcationType != FileInfo.ModificationType.DELETE) {
//							logger.info("need to retrieve " + remoteFile.name);
							files2bUpdated.add(remoteFile);
						}
					} else {
						FileInfo localFile = localDirInfo
								.getFileInfo(remoteFile.name);
						if (remoteFile.modificationTime > localFile.modificationTime) {
//							logger.info("need to retrieve " + remoteFile.name);
							files2bUpdated.add(remoteFile);
						}
					}

				}

				System.out.println("The following " + files2bUpdated + "files needs to be updated according to remote peer");
				for (FileInfo file2bUpdate : files2bUpdated) {
					System.out.println(file2bUpdate.toString());
				}

				// now update local directory accordingly
				for (FileInfo file2bUpdate : files2bUpdated) {
					if (file2bUpdate.modifcationType == FileInfo.ModificationType.DELETE) {
						logger.info("delete file: " + file2bUpdate.name);

						dirManager
								.deleteFileFromLocalDirectory(file2bUpdate.name);

					} else {
						try {
							logger.info("getting file " + file2bUpdate + " from remote");
							
							// download the new file and update local meta file
							syncCFT.getRemoteFile(runConfig.peerHost,
									runConfig.peerListeningPort,
									file2bUpdate.name, file2bUpdate.size,
									file2bUpdate.modificationTime);

							dirManager.addFileInfo(file2bUpdate);
						} catch (Exception e) {
							logger.error("meet exception getting remote file "
									+ file2bUpdate, e);
						}
					}

				}

			}

		} finally {
			syncCFT.cleanUp();
		}

	}

	private static void printUsage() {

		// CmdLineParser.Option peerPortOpt = parser.addIntegerOption('p',
		// "port");
		// CmdLineParser.Option peerHostOpt = parser.addStringOption('h',
		// "host");
		// CmdLineParser.Option fileOpt = parser.addStringOption('f', "file");
		// CmdLineParser.Option localPortOpt = parser.addIntegerOption('l',
		// "local_port");
		// CmdLineParser.Option localDirOpt = parser.addStringOption('d',
		// "local_dir");
		// CmdLineParser.Option fileSizeOpt = parser.addLongOption('s', "size");
		System.err
				.println("Usage: SyncCFTClientApp [{-p,--port} peerPortNumber] "
						+ "[{-h,--host} peerHost] "
						+ "[{-f,--file} remoteFileName]"
						+ "[{-l,--local_port} localPort]"
						+ "[{-d,--local_dir} localDirectory]"
						+ "[{-s,--size} remoteFileSize]");
	}
}
