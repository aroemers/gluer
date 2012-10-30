package test.modelb;

public abstract class ModelFactory {
	
	public static B get() {
		System.out.println("{test.modelb.ModelFactory} Creating new SubB.");
		return new SubB();
	}
}