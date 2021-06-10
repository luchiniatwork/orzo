# Orzo

[![CircleCI](https://circleci.com/gh/luchiniatwork/orzo.svg?style=shield&circle-token=c0bc81c8cc529f31a28565b9e4a246769ca8d623)](https://circleci.com/gh/luchiniatwork/orzo)
[![Clojars Project](https://img.shields.io/clojars/v/luchiniatwork/orzo.svg)](http://clojars.org/luchiniatwork/orzo)
[![cljdoc badge](https://cljdoc.org/badge/luchiniatwork/orzo)](https://cljdoc.org/d/luchiniatwork/orzo/CURRENT)
![MIT License](https://img.shields.io/badge/license-MIT-blue.svg)
![Beta Project](https://img.shields.io/badge/project%20status-beta-brightgreen.svg)

Orzo lets you manage the versioning strategy of any project from one
single place.

## Table of Contents

* [Getting Started](#getting-started)
* [Motivation](#motivation)
* [Usage](#usage)
* [Bugs](#bugs)
* [Help!](#help)

## Getting Started

Add `orzo` as a dependency in your `deps.edn` file (possibly as an
alias as shown below):

``` clojure
{:aliases
 {:orzo {:extra-deps
         {:luchiniatwork/orzo {:mvn/version "0.1.10"}}}}}
```

`orzo` has a series of composable functions specifically designed to
implement versioning strategy. You can see other example in
[Usage](#usage) below or refer to the [API
docs](https://cljdoc.org/d/luchiniatwork/orzo/CURRENT) to compose your
own strategy.

For the sake of this getting started, lets assume you want a semver
strategy that composes the major and minor from a text file with a
patch that is the job number of your build server.

Assumption one: you have a file `resources/base_version.txt` that
contains:

``` text
1.2
```

Assumption two: your build server places the build number on an
environment variable called `BUILD_NUM`.

Assumption three: you want to save the calculated version into a file
at `resources/version.txt` so that your users are able to identify
this built.

Assumption four: if everything goes fine (assuming your build server
runs tests as well) you want to tag your repo with the version number
prefaced with a `v` (as in `v0.4.542`).

In order to accomplish these you'll first need to create a script
called `gen_version.clj` on your classpath:

``` clojure
(ns gen-version
  (:require [orzo.core :as orzo]))

(defn -main [& args]
  (try
    (println (-> (orzo/read-file "resources/base_version.txt")
                 (orzo/extract-base-semver)
                 (orzo/set-semver {:patch (orzo/env "BUILD_NUM")})
                 (orzo/save-file "resources/version.txt")
                 (orzo/stage)))
    (System/exit 0)
    (catch Exception e
      (println e)
      (System/exit 1))))
```

`orzo` works with composable functions that can be easily piped with a
thread-first macro.

The first function `orzo/read-file` reads the first line of
`resources/base_version.txt` and returns its contents.

`orzo/extract-base-semver` will take that version just read and return
a semver object that can be manipulated.

For the next line we use `orzo/set-semver` to set the `:patch` of the
semver with whatever is the content of the environment variable
`BUILD_NUM` (read here with another `orzo` function `orzo/env`).

We then save the generated version with `orzo/save-file` so that it
can be read by users of this packaged later.

`orzo` also offers a convenient function for "staging" the version so
that it can be used at a later point. The function here is
`orzo/stage` and it's usually the last one in the script.

To run the script above:

``` shell
$ clojure -A:orzo -m gen-version
```

If your base version file is `1.2` and your build number is `451` you
should see:

``` text
1.2.451
```

Tip: if you are testing it locally and do not have an environment
version `BUILD_NUM` set, simply run:

``` shell
$ BUILD_NUM=451 clojure -A:orzo -m gen-version
```

Staging the version is important for the next script. We will call it
`tag_and_push_version.clj`:

``` clojure
(ns tag-and-push-version
  (:require [orzo.core :as orzo]
            [orzo.git :as git]))

(defn -main [& args]
  (try
    (println (-> (orzo/unstage)
                 (orzo/prepend "v")
                 (git/tag)
                 (git/push-tag)))
    (System/exit 0)
    (catch Exception e
      (println e)
      (System/exit 1))))
```

This script is recovering the version that was staged by the previous
script with `orzo/unstage`. Then it prepends it with a `v`, creates a
tag (`orzo/tag`) and pushes the tag back to your origin
(`orzo/push-tag`).

Of course you will only run this script if the build was
successful. In such case, run it with:

``` shell
$ clojure -A:orzo -m tag-and-push-version
```

## Motivation

Versioning is a concern in every single project. `orzo` is a simple,
configurable and extensible tool to implement any versioning strategy
your project requires.

## Usage

All functions withing `orzo` are composable via thread-first
macros. Such a practice allows for very short and easy-to-read scripts.

For a detailed insight over each function, do refer to the [API
docs](https://cljdoc.org/d/luchiniatwork/orzo/CURRENT).

The functions are grouped by informational functions, transformation
functions, validation functions and persistence functions.

**Informational functions** read some information from the system into the
stream. Examples are `orzo.core/env` or `orzo.core/read-file` that
read an environment variable or a file, respectively. Many other
functions fall into that category, for instance `orzo.git/last-tag`
that extracts the last tag from git.

**Transformation functions** alter the version in the
pipeline. Examples are `orzo.core/append`
`orzo.core/extract-base-semver` or `orzo.core/bump-semver`. These let
you append a value to the version, transform the version into semver
or bump certain semver indicators.

**Validation functions** will throw if something is wrong. For
instance `orzo.git/assert-clean?` will throw if the repo is not clean
locally.

**Persistence functions** are usually at the very end of `orzo`
scripts. They will have side effects and therefore must be used
carefully. `orzo.core/save-file` is a great example where a file is
saved with the version passed to it. Other examples are `orzo.git/tag`
and `orzo.git/push`. Both functions have implications on your git
repo (locally and remotely, respectively).

## Bugs

If you find a bug, submit a [GitHub
issue](https://github.com/luchiniatwork/orzo/issues).

## Help

This project is looking for team members who can help this project
succeed!  If you are interested in becoming a team member please open
an issue.

## License

Copyright © 2019 Tiago Luchini

Distributed under the MIT License.
