package test.adapter;

import gluer.Adapter;
import test.modela.A;
import test.modelb.B;

@Adapter
public class TwiceBtoA1 implements A {
		
	private B adaptee;

	public TwiceBtoA1(B adaptee) {
		this.adaptee = adaptee;
	}

	public void superSuperAMethod() {
		System.out.println("{test.adapter.TwiceBtoA1} How to implement this?");
	}

	public void superAMethod() {
		System.out.println("{test.adapter.TwiceBtoA1} Forwarding call to superBMethod.");
		adaptee.superBMethod();
	}

	public void aMethod() {
		System.out.println("{test.adapter.TwiceBtoA1} Forwarding call to bMethod.");
		adaptee.bMethod();
	}	
}