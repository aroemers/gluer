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

Note that no classes (other than those of the tool itself) are actually loaded into the JVM, when the tool is used in _checking_ mode. This means that no static initialisation in the classes referenced by the Adapters and .gluer specification will take place.

Since no errors are found, we can run our application using Gluer as a *runtime* framework:

```bash
$ java -cp . -javaagent:gluer.jar=example.config Client
Archiving: something neat
```



## Building

This project is written in Clojure 1.4. Building it requires [leiningen 2](https://github.com/technomancy/leiningen) to be installed. To build it, clone this repository and type `lein uberjar` in a terminal, while in the root directory of the clone. The 'standalone' JAR file can be found in the 'target' directory.



## Usage

### Adapter classes

Adapter classes are ordinary classes, that have some simple rules. 

* It needs to tagged with the `@gluer.Adapter` annotation.

* The types (classes and interfaces) it extends or implements determine __where__ the Adapter can be used. Currently there are no restrictions on this. This means an Adapter extending or implementing nothing is possible as well, but it would only be applicible for places where a `java.lang.Object` is expected.

* The single-argument non-primitive constructors determine __what__ an Adapter can "adapt". At least one such a constructor is required.

* The class needs to be declared _public_. If the class is a member of another class, it needs to be declared _static_ as well. The class cannot be abstract.


### Adapter resolution

To have an idea which Adapter will be chosen at runtime (and how resolution conflicts are determined when using the checker), have a look at the following rules used by the tool. The Adapter selection is based on the type _where_ the injection takes place and the type of _what_ is requested to be injected.

1. Check whether the _what_ type is a _where_ type (following the inheritance relations). If so, then we are done and the _what_ will be injected directly into the _where_. Otherwise, continue to 2.

2. Determine all eligible Adapters, by filtering all available Adapters on (a) whether it implements/extends a (sub)type of the _where_ type, and (b) if it has a single-argument constructor taking a (syper)type of the _what_ type. Based on the filtered result, the resolution continues as follows:
  * If none are found to be eligible, report this as an error (or throw a RuntimeException), for the injection cannot take place.
  * If one is found, we are done and that Adapter will be used for injection. Continue to step 5.
  * If more than one has been found, continue to step 3.


3. Determine the "closest" Adapter. We take the eligible Adapters from step 2 and filter them once more, to see which are the closest. Closest here means, which Adapter has a single-argument constructor that takes a type closest to the _what_ type, when looking at the inheritance hierarchy, and if more than one are found to be equally close at this point, the same is done for the _where_ type. Based on the filtered results, the resolution continues as follows:
  * If one is found (at least one is always found), we are done and that Adapter will be used. We continue at step 5.
  * If more than one has been found, continue to step 4.


4. Multiple Adapters are equally close. Now the tool looks at the declared precendences, if any. All equally close Adapters are filtered, by checking whether they are preceded by another Adapter in the list. If so, it will be removed from the list (It is removed at the end of this filtering process, so circular precedence rules would yield an empty list).
  * If this way the list has shrunk to one, we are done and that Adapter will be used. We continue at step 5.
  * If the list is empty, this signifies circular precedence declarations. These circular precedence declarations are already reported as warnings, by the checker, _before_ adapter resolution. If it actually occurs _during_ adapter resolution, it reports an error (or throws a RuntimeException).
  * If, however, the list still contains two or more Adapters, the framework cannot make a decision which Adapter to use and reports a resolution conflict error (or throws a RuntimeException).


5. A (most) suitable Adapter has been found at this point, and if the tool is used in _checking_ mode, then we are done. If, however, the tool is used in _runtime_ mode, it will also determine the "closest" constructor of that Adapter, based on the _what_ type. This constructor will be used for instantiating the Adapter.
  * It can occur that multiple constructors are equally close to the _what_ type. Currently, the constructor choice will be semi-random.


### Associations (.gluer files)

The injections (called associations) are in specified in .gluer files. Each associations takes a _where_ clause, a _what_ clause, and optionally specifies the _adapter_ to use. The lines in the .gluer file take the following form:

```
associate <where> with <what> [using <adapter>]
```

The following \<where> clauses are currently supported :

* `field <class>.<field>`: Means injecting the object into the \<field> of every instance of the specified \<class>. The injection takes place when a constructor of the specified class is called. The injection only takes place once, even if a constructor calls another constructor. The injected object is available to the statements and expressions in the constructor's body. Note that the field where the object is injected in, can be overwritten, either in the constructor or in some other method. An example: `... field somepackage.SomeClass.aField ...`.

The following \<what> clauses are currently supported:

* `new <class>`: Means injecting a new instance of the specified \<class> each time the \<where> clause triggers an injection. The class should have a non-argument constructor. An example: `... new somepackage.SomeClass ...`.

* `single <class>`: Means injecting a single instance of the specified \<class> each time the \<where> clause triggers an injection. In other words, the Gluer runtime instantiates the class only once and reuses it for every injection done by this association. The class should have a non-argument constructor. An example: `... single somepackage.SomeClass ...`.

* `call <class>.<method>([argument expressions])`: Means a call to a static (non-void) method each time the \<where> clause triggers an injection. The returned object is injected. This clause gives more expressive power, in case the former two \<where> clauses are not sufficient. An example: `... call somepackage.SomeFactory.get(MyConfig.isProduction()) ...`.

Optionally, one can specify which Adapter class should be used when an injection takes place, with `using <adapter>`. The \<adapter> needs to be a fully qualified name of the Adapter class. Adding this to a association overrules the automatic Adapter resolution as described above. This is one way to resolve resolution conflicts.

An association might also specify injections that are type compatible. This is perfectly fine, and the runtime will inject the result of the \<what> clause directly in the place designated by the \<where> clause. Such a direct injection will still take place through the Gluer runtime (i.e., it cannot be optimised), because one can write associations that sometimes need an Adapter and sometimes do not, depending on the actual runtime type of the \<what> clause (in particular the 'call' clause).

Another way of resolving resolution conflicts, is to declare precedence rules. For example, the following declares that, if `PreferredAdapter` and `PrecededAdapter` are equally suitable for a particular association, the `PreferredAdapter` will be used:

```
declare precedence PreferredAdapter over PrecededAdapter
```

In _checking_ mode, circular precedence declarations are detected and reported as warnings. If, however, an association statement may be hindered by these circular precedence declarations, an error is reported. Note that not all such errors can be detected statically, so it is good to pay attention to the warnings.

Precedence declarations and association statements can be mixed in a .gluer file, and their order does not matter. 

An example .gluer file:

```
associate field package.AClass.fieldFoo with new apackage.OtherClass using adapter.OtherClass2Foo

associate field package.AClass.fieldBar with single expensive.InitialisingService

associate field package.Alice.bob with call factories.Persons.create("Eve")

declare precedence adapter.betterversion.Eve2Bob over adapter.firstversion.Eve2Bob
```


### Configuration (.config files)

To keep the number of required command-line arguments to a minimum and to make a Gluer component easily distributable, a small configuration file is used as a way of communicating the necessary information to the framework. The same configuration file can (and should) be used for both the _checker_ as well as the _runtime_ mode of Gluer.

The format of the configuration is simple. Every line that contains at least one colon (:) is considered a key-value pair. Any subsequent colon after the first one is considered part of the value. All other lines are considered comment and are ignored.

The following keys are currently supported:

* `glue`: The value specifies a .gluer file, relative to the configuration file. This key can be specified multiple times.
* `verbose`: The value can be either 'true' or 'false'. If set to true, debug logging is displayed.

An example .config file:

```properties
This is just comment text, since it does not contain a colon.
Empty lines are ignored as well.

glue: path/to/file.gluer
glue: paths/are/relative/from/the/config.file
glue: more/colons:are:part/of/the.value

verbose: false
```


## Extending the framework

## Future improvements

The following points are possible future improvements. In no particular order:

* Generics and typed collections support. E.g. adapt a List\<Foo> to List\<Bar> by injecting a List\<Foo2Bar>
* Statically check for possible resolution conflicts occuring during run-time due to actual subtypes of _what_ is injected.
* Improved test suite, proving the correct implementation of implicit rules (such as adapter resolution) and correct coverage of static checks (such as detecting resolution conflicts)
* Injection in static fields
* Research possible means of disabling statements that overwrite the injected object

## License and disclaimer

Source Copyright © 2012 Arnout Roemers

Distributed under the [Eclipse Public License, v1.0](http://www.eclipse.org/legal/epl-v10.html)

This is a work in progress, use at own risk.
