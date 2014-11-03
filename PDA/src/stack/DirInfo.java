package stack;



import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.commons.lang3.builder.ReflectionToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;


/**
 * Data structure representing a directory.
 * 
 * @author shaohong
 * 
 */
public class DirInfo {

	public enum FilterType {
		BEFORE, AFTER
	};

	public String dirName;

	private List<FileInfo> files = new ArrayList<FileInfo>();

	public List<FileInfo> getFiles() {
		return files;
	}
	
	
	/**
	 * add file info to the DirInfo. later ones will override earlier ones 
	 * @param fileInfo
	 */
	public void addFileInfo(FileInfo newFileInfo){
		FileInfo existingFileInfo = getFileInfo(newFileInfo.name);
		
		if (null == existingFileInfo){
			files.add(newFileInfo);
		} else {
			if (newFileInfo.modificationTime >= existingFileInfo.modificationTime) {
				removeFileInfo(existingFileInfo.name);
				files.add(newFileInfo);
			}
		}
	}
	
	@Override
	public String toString() {
		return ReflectionToStringBuilder.toString(this,
				ToStringStyle.SIMPLE_STYLE, false, false);
	}

	public DirInfo getFilteredDirInfo(FilterType filterType, long timestamp) {
		DirInfo ret = new DirInfo();
		for (int i = 0; i < files.size(); i++) {
			FileInfo f = files.get(i);
			switch (filterType) {
			case BEFORE:
				if (f.modificationTime < timestamp) {
					ret.files.add(f);
				}
				break;

			case AFTER:
				if (f.modificationTime > timestamp) {
					ret.files.add(f);
				}
				break;
			default:
				break;
			}

		}

		return ret;
	}

	public boolean hasFile(FileInfo remoteFile) {
		for (FileInfo fileInfo: files){
			if (fileInfo.name.equalsIgnoreCase(remoteFile.name)){
				return true;
			}
		}
		
		return false;
	}

	public FileInfo getFileInfo(String name) {
		for (FileInfo fileInfo: files){
			if (fileInfo.name.equalsIgnoreCase(name)){
				return fileInfo;
			}
		}
		return null;
	}
	
	/**
	 * remove the info for the given name
	 * @param name
	 */
	public void removeFileInfo(String name) {
		Iterator<FileInfo> iterator = files.iterator();
	
		while (iterator.hasNext()){
			FileInfo fileInfo = iterator.next();
			if (fileInfo.name.equalsIgnoreCase(name)){
				iterator.remove();
				return;
			}
		}
		
	}

}
