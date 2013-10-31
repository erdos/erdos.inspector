# erdos.inspector

A better clojure.inspector

## Features

- Supports ininite lazy sequences
- Handles arrays and some java types.
- Displays atoms, refs, agents, vars. Shows change in real time.
- Fancy graphics

## Usage

```clojure
(use 'erdos.clojure)
```

Testing basic types.
```clojure
(inspect [nil :a-keyword 'a-symbool])
(inspect [{:a 1 :b 2 :c 2}
          #{:a :b :c}
          [[1 2] [3 4] [4 5 6]]])
(inspect (int-array [1 2 3 4 5]))
```

Some Java collections.
```clojure
(inspect (new java.util.HashMap {:a 1 :b 2}))
(inspect (new java.util.ArrayList [1 2 3 4]))
```

A lazy chunked seq
```clojure
(inspect (map inc [1 2 3 4 5]))
```
An infinite lazy seq
```clojure
(inspect (map * (range) (range)))
```


## License

Copyright © 2013 Janos Erdos

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
