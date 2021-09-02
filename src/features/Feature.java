package features;

public class Feature {
	public float max;
	public float min;
	private float value;
	public String name;
	
	public Feature(String name, float value, float min, float max){
		this.name = name;
		this.max = max;
		this.min = min;
		setValue(value);
	}
	
	public float getValue(){
		return value;
	}
	
	/**
	 * Sets the value, "truncating" it to the range [min, max]
	 * @param value
	 */
	public void setValue(float value){
		this.value = Math.max(min, Math.min(value, max));
	}
	
	
	/**
	 * Normalizes this feature via min-max scaling. The normalized value will be within [0, 1].
	 * The new value is computed as:
	 * newValue = (value - min) / (max - min)
	 */
	public void minMaxScaling(){
		value = (value - min) / (max - min);
	}
}
