# Third-party components

The release JAR contains FlatLaf, SQLite JDBC, SLF4J API and Logback. These components
remain under their respective upstream licenses. Copies of the relevant notices are in
`META-INF/licenses` in the JAR. An application image also contains a Java runtime and
the notices supplied by that runtime under its `legal` directory.

| Component | Version | Upstream license and source |
| --- | --- | --- |
| FlatLaf | 3.4.1 | Apache-2.0 · [source and license](https://github.com/JFormDesigner/FlatLaf/tree/3.4.1) |
| SQLite JDBC | 3.50.3.0 | Apache-2.0 and original Zentus notice · [corresponding source](https://github.com/xerial/sqlite-jdbc/tree/3.50.3.0) |
| SLF4J API | 2.0.17 | MIT · [corresponding source](https://github.com/qos-ch/slf4j/tree/v_2.0.17) |
| Logback Classic and Core | 1.5.18 | EPL-1.0 · [corresponding source](https://github.com/qos-ch/logback/tree/v_1.5.18) |

JUnit and Mockito are used exclusively in tests. Stockfish is an optional separate UCI
executable provided by the user. ChessMaster packages contain no Stockfish executable,
network or model files. Stockfish source and GPL notices are available from the
[Stockfish repository](https://github.com/official-stockfish/Stockfish).
