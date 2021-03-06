^title Classes

Magpie is a class-based language. That means everything you can stick in a
[variable](variables.html) will be an [*object*](objects.html) and every object is an instance of some *class*. Even [primitive types](primitives.html) like numbers and booleans are full-featured objects, as are [functions](functions.html).

Objects exist to *store data*, package it together, and let you pass it around. Objects also know their class, which can be used to select one method in a [multimethod](multimethods.html).

Unlike most object-oriented languages, classes in Magpie do not own methods. State and behavior are not encapsulated together. Instead, classes own state, and multimethods own behavior.

## Defining Classes

A class has a *name* and a set of *fields*, which describe the state the class holds. Unlike other dynamic languages, Magpie requires all of a class's fields to be explicitly declared. They aren't just loose bags of data. You can define a new class using the `defclass` keyword.

    :::magpie
    defclass Point
        var x
        var y
    end

This declares a simple class `Point`, with two fields `x` and `y`.

## Constructing Instances

Once you've defined a class, you instantiate new instances of it by calling the constructor method `new`. The left-hand argument to `new` is the class being instantiated, and the right-hand argument is a [record](records.html). The record has a named field for each field that the class defines. Given our above `Point` class, we can create a new instance like this:

    :::magpie
    val point = Point new(x: 2, y: 3)

### Overloading Initialization

Construction actually proceeds in two stages. When you invoke `new()`, it creates a fresh object of your class in some hidden location. Then it invokes the `init()` multimethod, passing in the same arguments. `init()` is responsible for initializing the fields of this hidden instance, but it doesn't return anything. After `init()` is done, `new()` returns the freshly-created and now fully-initialized object.

When you define a class, Magpie automatically creates a new specialization for `init()` that takes your class on the left, and a record of all of its fields on the right. This is called the *canonical initializer*. You can also provide your own specializations of `init()`. Doing so lets you overload constructors.

    :::magpie
    def (this == Point) init(x is Int, y is Int)
        this init(x: x, y: y)
    end

Here we've defined a new `init()` method that takes a `x` and `y` coordinates using a simple unnamed record. We can call it like this:

    :::magpie
    val point = Point new(2, 3)

When you call `new()` it looks for an `init()` method that matches *whatever* you pass to it. In this case, `2, 3` matches our overloaded `init()` method. That method in turn calls the canonical or "real" initializer to ensure that all of the class's fields are initialized.

This way, you are free to overload `init()` to make it easy to create instances of your classes. The only key requirement is that an `init()` method needs to eventually "bottom out" and call the canonical initializer before it returns. If you fail to do that, Magpie will [throw](error-handling.html) an `InitializationError` when you try to construct the object.

### Overloading Construction

In the above example, we provide an alternate path for *initializing* a new object, but we're still *creating* a new object normally. Sometimes you may need to be more flexible than that. Perhaps you want to cache objects so that calling `new()` with certain arguments always returns the *same* object.

    :::magpie
    def (this == Point) new(x is Int, y is Int)
        match x, y
            case 0, 0 then zeroPoint // Use cached one.
            else this new(x: x, y: y)
        end
    end

As you can see, you can also overload `new()` itself. If you do that, you can sidestep the process of creating a fresh instance entirely and return another existing object.

This also gives you the flexibility of creating an instance of a different class than what was passed in. You may want to hide the concrete class behind an abstract superclass, or switch out the concrete class based on some specific data passed in. Overloading `new()` gives you this flexibility without having to go through the trouble of implementing your own [factory](http://en.wikipedia.org/wiki/Factory_pattern).

## Fields

Once you have an instance of a class, you access a field by invoking a [getter](calls.html) on the object whose name is the name of the field.

    :::magpie
    val point = Point new(x: 2, y: 3)
    print(point x) // 2

Here, `point x` is a call to a method `x` with argument `point`. As you would expect, it returns the field's value.

### Assigning to Fields

Setting a field on an existing object looks like you'd expect:

    :::magpie
    val point = Point new(x: 2, y: 3)
    point x = 4
    print(point x)

**TODO: Explain that this is just a setter and point to assignment page.**

### Field Patterns

When defining a field, you may optionally give it a [pattern](patterns.html) after the field's name. If provided, then you will only be able to initialize or assign to the field using values that match that pattern.

    :::magpie
    defclass Point
        var x is Int
        var y is Int
    end

Here `x` and `y` are now constrained to number values by using `is Int` patterns. If you try to construct a `Point` using another type, or set a field using something other than an `Int`, you'll get an [error](error-handling.html).

### Immutable Fields

So far, we've seen fields defined using `var`, but you can also define them with `val`. Doing so creates an *immutable* field. Immutable fields can be initialized at construction time, but don't have a setter, so they can't be modified.

    :::magpie
    defclass ImmutablePoint
        val x is Int
        val y is Int
    end

    var point = ImmutablePoint new(x: 1, y: 2)
    point x = 2 // ERROR: There is no setter for "x".

### Field Initializers

Finally, when defining a field in a class, you can give it an initializer by having an `=` followed by an expression. If you do that, you won't need to pass in a value for the field when instantiating the class. Instead, it will automatically evaluate that initializer expression and set the field to the result.

    :::magpie
    defclass Point
        var x = 0
        var y = 0
    end

Here `Point`s will default to be `0, 0`. You can create a new one simply by doing:

    :::magpie
    val point = Point new()

## Inheritance

Like most class-based languages, Magpie supports *inheritance*. You can specify that one class is a *child* or *subclass* of another, like so:

    :::magpie
    defclass Widget
        val label is String
    end

    defclass Button is Widget
        var pressed? is Bool
    end

The part after `is` in the declaration of `Button` specifies its parent class. A child class inherits all of the state of its parent class. So here, `Button` will have fields for both `pressed?` and `label`.

When constructing a new instance of a class, its parent classes also need their constructors to be called so that inherited fields can be correctly initialized. To do this, the canonical initializer is extended to include a field for the parent class.

    :::magpie
    Button new(Widget: (label: "Play"), pressed?: false)

When the initializer for `Button` is called, it takes the value of the `Widget:` field and uses that as the argument to `Widget init(...)`. This way, child classes can invoke overridden parent class initializers, like so:

    :::magpie
    // Since there's just one field, don't require a record:
    def (this == Widget) init(label is String)
        this init(label: label)
    end

    // We can then call it through Button:
    Button new(Widget: "Play", pressed?: false)

If a parent class doesn't have any fields, or has initializers for all of them, you can omit it when constructing the child class. If the parent class has its own parent class, you may need to initialize it too:

    :::magpie
    defclass CheckBox is Button
        var checked? is Bool
    end

    CheckBox new(Button: (Widget: "45 RPM", pressed?: false),
            checked?: true)

That's a bit tedious, so you're encouraged to override the initializers for your class to "flatten" that out and hide the parent class initialization.

    :::magpie
    def (this == Button) init(label is String, pressed? is Bool)
        this init(Widget: label, pressed? pressed)
    end

    def (this == CheckBox) init(label is String, pressed? is Bool,
            checked? is Bool)
        // Use the init() for Button we just defined.
        this init(Button: (label, pressed?), checked?: checked?)
    end

    // Use the init() for CheckBox.
    CheckBox new("45 RPM", false, true)

You can consider canonical initializers to be "raw" initializers that you'll likely hide behind a simpler overridden one.

### Inherited Methods

If you call a [multimethod](multimethods.html) and pass in an instance of a child class, it will look through all of its methods to find a [pattern](patterns.html) that matches. A type pattern will match against an object of the given class, but it will also match objects of *child classes* of that class. In that way, methods defined using `is` patterns are automatically inherited by child classes.

    :::magpie
    def (this is Widget) display()
        print(this label)
    end

    val checkBox = CheckBox new("45 RPM", false, true)
    checkBox display() // Prints "45 RPM".

When multiple patterns match an object, type patterns on child classes take precedence over parent classes. This lets you *override* a method defined on a parent class.

    :::magpie
    def (this is CheckBox) display()
        val check = if this checked? then "[X] " else "[ ] "
        print(check + this label)
    end

    val checkBox = CheckBox new("45 RPM", false, true)
    checkBox display() // Prints "[X] 45 RPM".

Note that method inheritance like this only works for *type* patterns. Value patterns (i.e. `==` ones) do *not* match subclasses.

    :::magpie
    // Given a string like "Play|true", creates a new Button.
    def (this == Button) fromString(descriptor is String)
        val parts = descriptor split("|")
        this new Button(parts[0], parts[1] true?)
    end

    Button fromString("45 RPM|false")   // OK.
    CheckBox fromString("45 RPM|false") // ERROR: No method found.

This more or less follows other languages where "static" or "class" methods are not inherited, just "instance" ones.

### Multiple Inheritance

Unlike many newer languages, Magpie embraces multiple inheritance. A class is free to have as many parent classes as it wishes:

    :::magpie
    defclass Container
        val widgets is List
    end

    defclass GroupBox is Widget, Container
        var selected is Int
    end

Here, `GroupBox` inherits from both `Widget` and `Container`. This means it gets all of the fields of both of those parents (and their parents). In addition, methods specialized to either of those are valid for `GroupBox` objects too. When constructing an object, all of its parent classes must be initialized.

    :::magpie
    GroupBox new(
            Widget: "Group",
            Container: (widgets: []),
            selected: 0)

Here, we're passing fields for both `Widget:` and `Container:`.

Multiple inheritance can add considerable complexity to a language, which is why many shy away from it. Most of that complexity can be traced back to a single corner case: *inheriting from the same class twice*. For example:

    :::magpie
    defclass RadioButton is Button, Widget
    end

    val group = RadioButton new(
            Button: ("From Button", false),
            Widget: "From Widget")
    print(group label) // ???

Here, `RadioButton` inherits `Widget` along two paths: once from `Button`, and once from `Widget` directly. Both paths try to initialize the label, one using `"From Button"` and one using `"From Widget"`. How do we decide which one "wins"? Overriding methods have similar ambiguity problems.

To avoid these problems, Magpie has a simple rule: *a class may only inherit from some other class once, either directly or indirectly.* In other words, there may only be one path from a given child class to a given parent class. If you try to define a class like `RadioGroup` above, Magpie will [throw](error-handling.html) `ParentCollisionError` at you.

A side-effect of this rule is that Magpie doesn't have a "root" class like `Object` or `Any` in other languages. It doesn't need one. Root classes usually don't have state, and you can define methods that work on objects of any class (think `toString()` or `getHashCode()`) simply by defining methods that aren't specialized to any type.
