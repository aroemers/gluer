package test.modelb;

public class SuperB {
	
	public void superBMethod() {
		print("superBMethod called.");
	}

	protected void print(final String message) {
		String className = this.getClass().getName();
		System.out.println("<"+ className +"> "+ message);
	}
}