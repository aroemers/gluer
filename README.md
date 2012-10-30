# Gluer

An Adapter-aware Dependency Injection Framework for Java.

## About

Gluer is a lightweight framework for injecting objects into other object's fields, where the injected object need not be type-compatible with the field's type. The injections are competely external, i.e. no changes in any of the classes are required. The framework uses specialised Adapter classes for type-incompatible injections.

## Example

Say we have the following interface and class in one component:

```java
public interface Service {
	public void do(String text);
}

public class Client {

	private Service service;

	public Client() {
		service.do("something neat");
	}

	public static void main(String[] args) {
		new Client();
	}
}
```

We see here that class `Client` uses some `Service` that is stored in a field. Say we also have the following class in another, separatly developed, component:

```java
public class Archiver {
	
	public void archive(String text) {
		System.out.println("Archiving: "+ text);
		// ... archive text.
	}
}
```

The class `Archiver` is a good candidate for being used as a service. So, if want to use an `Archiver` object in a `Client` object, i.e. inject the `Archiver` in its `service` field, we can specify this in file, for instance in `example.gluer`.

```
associate field Client.service with new Archiver
```

Notice that the types are not compatible. Above statement would inject an `Archiver` object into a field of type `Service`. This is where Adapter classes come in. For this example, we would need to define the following Adapter:

```java
import gluer.Adapter;

@Adapter
public class Archiver2Service implements Service {
	
	private Archiver adaptee;

	public Archiver2Service(Archiver adaptee) {
		this.adaptee = adaptee;
	}

	public void do(String text) {
		this.adaptee.archive(text);
	}
}
```

Since the `Archiver2Service` is tagged with the `@Adapter` annotation, the Gluer framework recognizes this class and registers it as an available Adapter. Whenever Gluer is requested to inject an `Archiver` (or a subtype of it) object into a field of type `Service` (or a supertype of it), like in our example, it will automatically use above `Archiver2Service` to "adapt" the method calls.

Gluer has two modes of usage, i.e. a **checking** mode and a **runtime** mode. We can check if our injections will work using Gluer as a *checking* tool. For this we need to write a small configuration file, for instance in `example.config`:

```properties
glue: client.gluer
verbose: false
```

Checking our .gluer specifications and the Adapter classes goes as follows (assuming all above classes are in the current directory):

```bash
$ java -cp . -jar gluer.jar example.config
No errors.
```

Since no errors are found, we can run our application using Gluer as a *runtime* framework:

```bash
$ java -cp . -javaagent:gluer.jar=example.config Client
Archiving: something neat
```

## Usage

### Adapter classes

Adapter classes are ordinary classes, that have some simple rules. 

* It needs to tagged with the `@gluer.Adapter` annotation.

* The types (classes and interfaces) it extends or implements determine __where__ the Adapter can be used. Currently there are no restrictions on this. This means an Adapter extending or implementing nothing is possible as well, but it would only be applicible for places where a `java.lang.Object` is expected.

* The single-argument non-primitive constructors determine __what__ an Adapter can "adapt". At least one such a constructor is required.

* The class needs to be declared _public_. If the class is a member of another class, it needs to be declared _static_ as well.

### Adapter resolution

### Associations (.gluer files)

### Configuration (.config files)

## Future improvements

* Generics support
* Plain, adapter-less injections

## License and disclaimer

Copyright © 2012 Arnout Roemers

License to be determined.

This is a work in progress, use at own risk.
