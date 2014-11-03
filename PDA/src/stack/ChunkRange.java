package stack;

import org.apache.commons.lang3.builder.EqualsBuilder;

public class ChunkRange {

	public long startChunkId;
	public long endChunkId;
	
	public ChunkRange(long startChunkId, long endChunkId) {
		super();
		this.startChunkId = startChunkId;
		this.endChunkId = endChunkId;
	}
	
	
	
	public static ChunkRange parseChunkRangeHeader(String chunkRangeVal) {
		String[] strings = chunkRangeVal.split("-");

		if (strings.length != 2) {
			return null;
		}

		long startChunkId = Long.parseLong(strings[0]);
		long endChunkId = Long.parseLong(strings[1]);

		if (startChunkId > endChunkId) {
			return null;
		} else {
			return new ChunkRange(startChunkId, endChunkId);
		}

	}
	
	@Override
	public boolean equals(Object obj) {
		if (obj instanceof ChunkRange == false) {
			return false;
		}
		if (this == obj) {
			return true;
		}
		
		ChunkRange rhs = (ChunkRange) obj;
		return new EqualsBuilder()
			.append(startChunkId, rhs.startChunkId)
			.append(endChunkId, rhs.endChunkId)
			.isEquals();
	}



	public long getTotalNumberOfChunks() {
		return (endChunkId - startChunkId + 1);
	}
}
