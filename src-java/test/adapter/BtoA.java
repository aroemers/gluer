package test.adapter;

import gluer.Adapter;
import test.modela.A;
import test.modelb.B;

@Adapter
public class BtoA implements A {
	
	private B adaptee;

	public BtoA(B adaptee) {
		this.adaptee = adaptee;
	}

	public void superSuperAMethod() {
		System.out.println("{test.adapter.BtoA} How to implement this?");
	}

	public void superAMethod() {
		System.out.println("{test.adapter.BtoA} Forwarding call to superBMethod.");
		adaptee.superBMethod();
	}

	public void aMethod() {
		System.out.println("{test.adapter.BtoA} Forwarding call to bMethod.");
		adaptee.bMethod();
	}
}