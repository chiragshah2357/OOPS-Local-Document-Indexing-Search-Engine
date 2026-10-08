/*
 * Demo documents.
 *
 * Used only while the frontend runs in mock mode (see USE_MOCK in api.js),
 * so the UI can be tried before the Java backend exists. Once the backend is
 * connected, the real documents come from the folder the user indexes.
 */
window.MOCK_DOCS = [
  {
    id: 1,
    title: "java-collections.txt",
    path: "notes/java-collections.txt",
    text: "The Java Collections Framework provides ready made data structures. A HashMap stores key value pairs and gives constant time lookup on average, while a HashSet stores unique elements and is backed by a HashMap. An ArrayList grows dynamically, and a PriorityQueue always returns the smallest element first. Choosing the right collection decides how fast a Java program runs."
  },
  {
    id: 2,
    title: "threads-and-synchronization.txt",
    path: "notes/threads-and-synchronization.txt",
    text: "A thread is the smallest unit of execution in Java. Multithreading lets a program do several things at once, but threads that share data need synchronization to avoid race conditions. Use the synchronized keyword or a ReentrantLock so only one thread changes the shared balance at a time. A deadlock happens when two threads wait on each other forever."
  },
  {
    id: 3,
    title: "file-io-basics.txt",
    path: "notes/file-io-basics.txt",
    text: "File I/O in Java reads and writes data using streams. A BufferedReader reads a text file line by line, and a FileWriter writes text back to disk. Always close a file after use, or let try with resources do it for you. A Java program can walk a whole folder and read every file inside it."
  },
  {
    id: 4,
    title: "oop-principles.txt",
    path: "notes/oop-principles.txt",
    text: "Object oriented programming is built on four ideas: encapsulation, inheritance, polymorphism and abstraction. Encapsulation hides data inside a class. Inheritance lets a class reuse another class. Polymorphism lets one interface have many forms, and abstraction hides the details behind a simple interface."
  },
  {
    id: 5,
    title: "exception-handling.txt",
    path: "notes/exception-handling.txt",
    text: "An exception is an event that interrupts the normal flow of a Java program. Use try, catch and finally to handle it. Checked exceptions such as IOException must be handled or declared, while unchecked exceptions come from programming mistakes. A custom exception class makes errors easier to understand."
  },
  {
    id: 6,
    title: "search-engine-notes.txt",
    path: "notes/search-engine-notes.txt",
    text: "A search engine builds an inverted index that maps every word to the documents containing it. To answer a query it looks up each word, combines the matching documents, and ranks them by keyword frequency. Searching an index is much faster than reading every file again, because each word is a single HashMap lookup."
  }
];
