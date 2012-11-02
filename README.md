# Gluer

An Adapter-aware Dependency Injection Framework for Java.

## About

Gluer is a lightweight framework for injecting objects into other object's fields, where the injected object need not be type-compatible with the field's type. The injections are competely external, i.e. no changes in any of the classes are required. The framework uses specialised Adapter classes for type-incompatible injections.

## Example

Let's look at a simple example. Say we have the following interface and class in one component:

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

We see here that class `Client` uses some `Service` object that is referenced in a field. Say we also have the following class in another, separatly developed, component:

```java
public class Archiver {
	
	public void archive(String text) {
		System.out.println("Archiving: "+ text);
		// ... archive text.
	}
}
```

The class `Archiver` is a good candidate for being used as a service. So, if want to use an `Archiver` object in a `Client` object, i.e. inject the `Archiver` in its `service` field, we can specify this in a file, for instance in `example.gluer`, having the following line:

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

Gluer has two modes of usage, i.e. a **checking** mode and a **runtime** mode. We can check if our injections will work using Gluer as a *checking* tool. Both modes require a small configuration file, for instance in `example.config`:

```properties
glue: example.gluer
```

Above configuration tells the tool to use one .gluer file, namely 'example.gluer'. Checking our .gluer specifications and the Adapter classes goes as follows (assuming all above classes are in the current directory):

```bash
$ java -cp . -jar gluer.jar example.config
No errors.
```

Since no errors are found, we can run our application using Gluer as a *runtime* framework:

```bash
$ java -cp . -javaagent:gluer.jar=example.config Client
Archiving: something neat
```

## Building

This project is written in Clojure 1.4. Building it requires [leiningen 2](https://github.com/technomancy/leiningen) to be installed. To build it, clone this repository and type `lein uberjar` in a terminal, while in the root directory of the clone.

## Usage

### Adapter classes

Adapter classes are ordinary classes, that have some simple rules. 

* It needs to tagged with the `@gluer.Adapter` annotation.

* The types (classes and interfaces) it extends or implements determine __where__ the Adapter can be used. Currently there are no restrictions on this. This means an Adapter extending or implementing nothing is possible as well, but it would only be applicible for places where a `java.lang.Object` is expected.

* The single-argument non-primitive constructors determine __what__ an Adapter can "adapt". At least one such a constructor is required.

* The class needs to be declared _public_. If the class is a member of another class, it needs to be declared _static_ as well.

### Adapter resolution

### Associations (.gluer files)

The injections (called associations) are in specified in .gluer files. Each associations takes a _where_ clause, a _what_ clause, and optionally specifies the _adapter_ to use. The lines in the .gluer file take the following form:

```
associate <where> with <what> [using <adapter>]
```

The following \<where\> clauses are currently supported :

* `field <class>.<field>`: Means injecting the object into the \<field\> of every instance of the specified \<class\>. An example: `... field somepackage.SomeClass.aField ...`.

The following \<what\> clauses are currently supported:

* `new <class>`: Means injecting a new instance of the specified \<class\> each time the \<where\> clause triggers an injection. The class should have a non-argument constructor. An example: `... new somepackage.SomeClass ...`.

* `single <class>`: Means injecting a single instance of the specified \<class\> each time the \<where\> clause triggers an injection. In other words, the Gluer runtime instantiates the class only once and reuses it for every injection done by this association. The class should have a non-argument constructor. An example: `... single somepackage.SomeClass ...`.

* `call <class>.<method>([argument expressions])`: Means a call to a static (non-void) method each time the \<where\> clause triggers an injection. The returned object is injected. This clause gives more expressive power, in case the former two \<where\> clauses are not sufficient. An example: `... call somepackage.SomeFactory.get(MyConfig.isProduction()) ...`.

Optionally, one can specify which Adapter class should be used when an injection takes place, with `using <adapter>`. The \<adapter\> needs to be a fully qualified name of the Adapter class. Adding this to a association overrules the automatic Adapter resolution as described above. Currently this is the only means to mitigate resolution conflicts.

An association might also specify injections that are type compatible. This is perfectly fine, and the runtime will inject the result of the \<what\> clause directly in the place designated by the \<where\> clause. Such a direct injection will still take place through the Gluer runtime (i.e., it cannot be optimised), because one can write associations that sometimes need an Adapter and sometimes do not, depending on the actual runtime type of the \<what\> clause (in particular the 'call' clause).

An example .gluer file:

```
associate field package.AClass.fieldFoo with new apackage.OtherClass using adapter.OtherClass2Foo

associate field package.AClass.fieldBar with single expensive.InitialisingService

associate field package.Alice.bob with call factories.Persons.create("Eve")
```

### Configuration (.config files)

To keep the number of required command-line arguments to a minimum and to make a Gluer component easily distributable, a small configuration file is used as a way of communicating the necessary information to the framework. The same configuration file can (and should) be used for both the _checker_ as well as the _runtime_ mode of Gluer.

The format of the configuration is simple. Every line that contains at least one colon (:) is considered a key-value pair. Any subsequent colon after the first one is considered part of the value. All other lines are considered comment and are ignored.

The following keys are currently supported:

* `glue`: The value specifies a .gluer file, relative to the configuration file. This key can be specified multiple times.
* `verbose`: The value can be either 'true' or 'false'. If set to true, debug logging is displayed.
* `plug-in`: The value specifies the namespace path to a plug-in file. See the section on 'Extending the framework' below. This key can be specified multiple times.
* `classpath-entry`: The value specifies a class-path entry, which will be added to the standard classpath automatically. This key can be specified multiple times.

An example .config file:

```properties
This is just comment text, since it does not contain a colon.
Empty lines are ignored as well.

glue: path/to/file.gluer
glue: paths/are/relative/from/the/config.file
glue: more/colons:are:part/of/the.value

plug-in: gluer.plugin.what-call

classpath-entry: adapters.jar

verbose: false
```


## Extending the framework

## Future improvements

* Generics support
* Plug-in system
* Class-path entries in configuration

## License and disclaimer

Source Copyright © 2012 Arnout Roemers

Distributed under the [Eclipse Public License, v1.0](http://www.eclipse.org/legal/epl-v10.html)

This is a work in progress, use at own risk.
