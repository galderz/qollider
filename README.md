# Qollider - The Quarkus particle accelerator

<p align="left">
  <a href="https://github.com/galderz/qollider"><img alt="GitHub Actions status" src="https://github.com/galderz/qollider/workflows/Java%20CI/badge.svg"></a>
</p>

## Intro

Qollider is an API for building and testing Quarkus, its dependencies and related projects.

## Dependencies

Qollider users require Java 17 or higher.

## Examples

See the `examples` folder for JBang scripts that use Qollider.

## How it works

When Qollider starts, it creates a `~/.qollider` folder,
where it will install its dependencies.

Then, it creates a folder for the day on which the Qollider is executed,
e.g. `~/.qollider/cache/DDMM`.
This folder contains Qollider run specific tools and source directories.
These elements will be built as per the instructions of each Qollider plan invocation.
This means that if you execute the same code on two different days,
Qollider will download and build them again.
This can be very useful to start from a clean slate.

Qollider uses marker files to signal that a given step within a script has completed.
Assuming Qollider gets executed again on the same day,
these markers allow steps executed previously to be skipped.
Each marker file contains information of the executed step,
so you can easily inspect them and delete them to re-execute a given step.
Re-executing a given step might also require deleting a particular folder,
if the step involved downloading certain artifact,
or cloning a repository.
