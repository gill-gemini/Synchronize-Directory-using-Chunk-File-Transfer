import org.apache.commons.lang3.builder.ToStringBuilder;


public class RunConfig {

	static final int defaultListeningPort = 6000;
	
	int peerListeningPort;
	String peerHost = null;
	String fileRequested = null;
	long fileSize;
	
	int localListeningPort = defaultListeningPort;
	String localDir = ".";
	String command;

	public String toString() {

		return ToStringBuilder.reflectionToString(this);

	}
}
