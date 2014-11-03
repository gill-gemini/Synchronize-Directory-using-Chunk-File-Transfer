package stack;

import java.util.Random;

/**
 * 
 * @author shaohong
 *
 */
public class LossModel {
	public enum LossState {Loss, NoLoss};
	double l2lRate;
	double nl2lRate;
	
	private LossState state;
	private Random generator = new Random();
	
	public LossModel(double loss2loss, double noLoss2Loss) {
		super();
		this.l2lRate = loss2loss;
		this.nl2lRate = noLoss2Loss;
		
		// initial state is NoLoss
		state = LossState.NoLoss;
	}
	
	/**
	 *  return the current state and also do some transitions according to the
	 *  Marchov model.
	 */
	public synchronized LossState getState(){
		LossState currState = state;
		
		// calculating the next state;
		double dice = generator.nextDouble();
		
		if (currState == LossState.Loss) {
			if (dice < l2lRate) {
				state = LossState.Loss;
			} else {
				state = LossState.NoLoss;
			}
		}
		
		if (currState == LossState.NoLoss) {
			if (dice < nl2lRate) {
				state = LossState.Loss;
			} else {
				state = LossState.NoLoss;
			}
		}
		
		return currState;
	}
	
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder("LossModel");
		sb.append("[");
		sb.append("l2lRate=").append(l2lRate);
		sb.append(", ");
		sb.append("nl2lRate=").append(nl2lRate);
		sb.append(", ");
		sb.append("currState=").append(state.name());
		sb.append("]");
		
		return sb.toString();
	}
	
	
}
