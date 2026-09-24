// Build configuration shared by every main in this directory.
//
// Build (one native binary):
//   scala-cli --power package scala -o bin/agaton --native-image -f \
//     --main-class agaton.AgatonMain

//> using scala 3.9.0
//> using dep com.lihaoyi::ujson:4.4.3
//> using options -deprecation -feature -Wunused:all
//> using packaging.graalvmJvmId graalvm-community:25.0.2
//> using packaging.graalvmArgs --no-fallback -O2 -H:+ReportExceptionStackTraces
