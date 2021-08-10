[![Build Status](https://img.shields.io/github/workflow/status/lion7/gradle-jpms-plugin/Build)](https://github.com/lion7/gradle-jpms-plugin/actions?query=workflow%3A%22Build%22)


Introduction
===

A Gradle plugin that adds support for the Java Platform Module System
The plugin is published in the [Gradle plugin repository](https://plugins.gradle.org/plugin/nl.leeuwit.gradle.jpms).

It makes building modules seamless from the Gradle perspective. 
It automatically generates a `module-info.java` for your main JAR, compiles it and adds it to the JAR at the appropriate place.

Limitations
===

Please file issues if you run into any problems or have additional requirements!

Requirements
===

This plugin requires JDK 11 or newer to be used when running Gradle.

The minimum Gradle version supported by this plugin is 6.0.

Contributing
===

Please tell us if you're using the plugin on Twitter using [@gerarddeleeuw](https://twitter.com/gerarddeleeuw)!
We would also like to hear about any issues or limitations you run into.
Please file issues in the Github project.
Bonus points for providing a test case that illustrates the issue.

Contributions are very much welcome.
Please open a Pull Request with your changes.
Make sure to rebase before creating the PR so that the PR only contains your changes, this makes the review process much easier.
Again, bonus points for providing tests for your changes.
