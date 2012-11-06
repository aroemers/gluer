package test.adapter;

import gluer.Adapter;
import test.modela.A;
import test.modelb.SubB;
import test.modelb.SubSubB;

@Adapter
public abstract class SubBtoA implements A {
	
	private SubB adaptee;

	public SubBtoA(SubB adaptee) {
		this.adaptee = adaptee;
	}

	public SubBtoA(SubSubB adaptee) {
		System.out.println("{test.adapter.SubBtoA} Called more specialized constructor.");
		this.adaptee = adaptee;
	}

	public void superSuperAMethod() {
		System.out.println("{test.adapter.SubBtoA} How to implement this?");
	}

	public void superAMethod() {
		System.out.println("{test.adapter.SubBtoA} Forwarding call to superBMethod.");
		adaptee.superBMethod();
	}

	public void aMethod() {
		System.out.println("{test.adapter.SubBtoA} Forwarding call to bMethod.");
		adaptee.bMethod();
	}
}