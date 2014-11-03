package stack;

import org.junit.Test;

public class TestLossModel {

	
	@Test
	public void test() {
		double q = 0.5;
		double p = 1;
		LossModel lossModel = new LossModel(q, p);
		
		int lossStateCounter = 0;
		int nolossStateCounter = 0;
		
		for (int i=0; i<10000; i++){
			if (lossModel.getState() == LossModel.LossState.Loss) {
				lossStateCounter ++;
			} else {
				nolossStateCounter ++;
			}
		}
		
		System.err.println("loss2loss transition rate: q=" + q + ", NoLoss2Loss transition rate: p=" + p);
		System.err.println("lossStateCounter = " + lossStateCounter);
		System.err.println("nolossStateCounter = " + nolossStateCounter);		
	}

}
