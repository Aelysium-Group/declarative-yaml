# 👋 Meet Declarative YAML
Declarative YAML (DYAML) is a declarative way to create and load yaml config files.
Originally build for [RustyConnector](https://github.com/Aelysium-Group/rustyconnector-core),
DYAML dynamically reads the members of the declared config class in order to infer details such as type, name, and more!

```gradle
maven {
    url "https://maven.mrnavastar.me/releases"
}
```

```gradle
implementation "group.aelysium:declarative-yaml:0.1.0"
```

# ⚠️ Config print formatting is not currently finished! ⚠️

## Quick Intro
To get started, use the DeclarativeYAML class.

### Creating a Config
Config classes must be annotated with the `@Config` annotation.
The path provided to the annotation will generate a config relative to the classloader's root.
### Adding Entries
An entry is defined as a non-static, final member, annotated with both `@Comment` and `@Node` or just a single Comment/Node annotation.<br/>
Comments and Nodes will be printed to the configuration in the order that you define in the annotations.
If a Comment and Node have the same order, the comment will be printed first.
### Entry Serialization
When a member is annotated with `@Node` the value of that entry in the config will be loaded into that member.
The value itself will be serialized using `TypeToken` into whatever the member is.
### Configuration Injection
Declarative YAML supports config injection. What this means is that, two config classes can point to the same config.
If they have differing entries, those entries will be injected into the correct location in the config, and will also be loaded as such.
### Header Comment
If you want to add a comment that always appears at the top of the config;
simply use the `@Config` annotation on the class declaration, just below the `@Config` annotation.
### All Contents
The `@AllContents` annotation will load all bytes from the configuration into the member it's assigned to.
Just like entries, the member must be non-static and final. The member must also be a `byte[]`.
### Path Parameters
Via the `@PathParameter` annotation, you can extract specific values from the config path into a member.
Just like entries, the member used must be non-static and final. The member can be any type you want, the value in the path will attempt to be serialized into the type you specify.
```
/config/{identifier}.yml
```
Any value specified in place of {identifier} will be loaded into the defined member annotated with `@PathParameter("identifier")`
In order to define what values to replace in the path, you can use the pathReplacements parameter in this method.

2024 © [Aelysium](https://aelysium.group)
