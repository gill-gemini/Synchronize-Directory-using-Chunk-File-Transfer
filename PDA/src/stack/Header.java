package stack;

public class Header {

	public static final String HN_CSeq = "CSeq";
	public static final String HN_SessionID = "SessionID";
	public static final String HN_ChunkRange = "ChunkRange";
	public static final String HN_ChunkID = "ChunkID";
	public static final String HN_NumberOfChunks = "NumberOfChunks";
	public static final String HN_LastSyncStamp = "LastSyncTimeStamp";
	public static final String HN_Stamp = "TimeStamp";
	public static final String HN_PN = "PN"; // packet number	
	
	private String name;
	private String value;
	
	public Header(String name, String value) {
		super();
		this.name = name;
		this.value = value;
	}
	
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	public String getValue() {
		return value;
	}
	public void setValue(String value) {
		this.value = value;
	}
	
	
	/**
	 * Create the CSeq header value;
	 * @param cseq
	 * @param method
	 * @return
	 */
	public static String createCseqHdrValue(int cseq, String method){
		
		StringBuilder sb = new StringBuilder();
		sb.append( cseq );
		sb.append(" ");
		sb.append(method);
		
		return sb.toString();
	}
}
