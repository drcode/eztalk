# eztalk

The simplest, most low-effort way to make two clojure programs talk to each other over a network. The library only has two commands!

Built on top of ezzmq. 

# Instructions

## Step 1- From the first node, start up eztalk:


```clojure
(with-eztalk (let [foo (start (fn [data]
                                 (println "I got the data:" data)))]
                  ...))
```


You wrap all your logic in the `with-eztalk` macro, which frees up all the resources at the end (sockets, etc). The `start` command is handed only a callback function which is where all the data sent to this node will end up.

## Step 2- From the second node, do the same thing, BUT ALSO pass in the network address of the first node:


```clojure
(with-eztalk (let [foo (start (fn [data]
                                 (println "I got the data:" data))
                              "232.122.1.34")]
                  ...))
```

The nodes should now be fully connected!

## Step 3- Simply call the returned function to send data to the other node:


```clojure
(foo 42)

[on other node...]

> I got the data: 42
```

You can send data in either direction. The data can be any edn data structure.

That's all this library does!
