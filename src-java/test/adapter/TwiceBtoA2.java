package test.adapter;

import gluer.Adapter;
import test.modela.A;
import test.modelb.B;

@Adapter
public class TwiceBtoA2 implements A {
		
	private B adaptee;

	public TwiceBtoA2(B adaptee) {
		this.adaptee = adaptee;
	}

	public void superSuperAMethod() {
		System.out.println("{test.adapter.TwiceBtoA2} How to implement this?");
	}

	public void superAMethod() {
		System.out.println("{test.adapter.TwiceBtoA2} Forwarding call to superBMethod.");
		adaptee.superBMethod();
	}

	public void aMethod() {
		System.out.println("{test.adapter.TwiceBtoA2} Forwarding call to bMethod.");
		adaptee.bMethod();
	}	
}