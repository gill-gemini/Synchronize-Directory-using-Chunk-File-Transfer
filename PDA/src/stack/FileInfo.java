package stack;


import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class FileInfo {

	public enum ModificationType{CREATE, MODIFY, DELETE};
	
	public String name;
	
	public long size;
	
	public String checkSum;
	
	public long modificationTime;	//the number of milliseconds since January 1, 1970, 00:00:00 GMT
	
	public ModificationType modifcationType;
	
	@Override
	public String toString(){
		return ReflectionToStringBuilder.toString(this,
				ToStringStyle.SHORT_PREFIX_STYLE, false, false);		
	}
}
