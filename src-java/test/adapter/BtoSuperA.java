package test.adapter;

import gluer.Adapter;
import test.modela.SuperA;
import test.modelb.B;

@Adapter
public class BtoSuperA implements SuperA {
	
	private B adaptee;

	public BtoSuperA(B adaptee) {
		this.adaptee = adaptee;
	}

	public void superSuperAMethod() {
		System.out.println("{test.adapter.BtoSuperA} How to implement this?");
	}

	public void superAMethod() {
		System.out.println("{test.adapter.BtoSuperA} Forwarding call to superBMethod.");
		adaptee.superBMethod();
	}
}