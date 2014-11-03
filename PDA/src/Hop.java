
public class Hop {

	String peerHost;

	int peerPort;

	public String getPeerHost() {
		return peerHost;
	}

	public void setPeerHost(String peerHost) {
		this.peerHost = peerHost;
	}

	public int getPeerPort() {
		return peerPort;
	}

	public void setPeerPort(int peerPort) {
		this.peerPort = peerPort;
	}

	public static Hop parseHopInfo(String peerConnectionString) {
		String[] strings = peerConnectionString.split(":");
		Hop ret = new Hop();
		ret.peerHost = strings[0];
		ret.peerPort = Integer.parseInt(strings[1]);
		return ret;
	}

}
