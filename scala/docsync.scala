package agaton

import java.nio.file.{Files, Path, Paths}
import scala.util.matching.Regex

/** docs/handbook.md embeds the full text of the sources in this directory. Those
  * blocks are generated, and this keeps them honest.
  *
  *   scala-cli run scala --main-class agaton.DocSyncMain
  *   scala-cli run scala --main-class agaton.DocSyncMain -- --check
  *
  * `--check` writes nothing and exits 1 if any block has drifted, so it works
  * as a pre-commit or CI gate. Blocks are rewritten back-to-front so that
  * replacing one does not invalidate the offsets of the ones above it.
  */
object DocSyncMain:

  private val Heading: Regex = """(?m)^### `scala/([A-Za-z0-9_.]+)`""".r
  private val Fence = "```scala\n"

  /** The REPOSITORY, not a user's configuration home: this tool maintains the
    * checked-in handbook, so it must not follow AGATON_HOME. The working
    * directory, or its parent when run from inside `scala/`; AGATON_REPO wins.
    */
  private def repo: Path =
    sys.env.get("AGATON_REPO").map(Root.expand).getOrElse:
      val cwd = Paths.get("").toAbsolutePath
      if Files.isDirectory(cwd.resolve("scala")) then cwd else cwd.getParent

  def main(args: Array[String]): Unit =
    val check = args.contains("--check")
    val doc = repo.resolve("docs/handbook.md")
    val srcDir = repo.resolve("scala")

    if !Files.exists(doc) then
      Console.err.println(s"docsync: no $doc")
      sys.exit(2)

    var text = Files.readString(doc)
    var drifted = List.empty[String]
    var matched = 0

    for m <- Heading.findAllMatchIn(text).toList.reverse do
      val name = m.group(1)
      val file = srcDir.resolve(name)
      if !Files.exists(file) then
        Console.err.println(s"docsync: $name is embedded but missing from ${srcDir}")
        sys.exit(2)
      matched += 1
      val fenceStart = text.indexOf(Fence, m.end)
      if fenceStart < 0 then
        Console.err.println(s"docsync: no ```scala block after the heading for $name")
        sys.exit(2)
      val bodyStart = fenceStart + Fence.length
      val bodyEnd = text.indexOf("\n```", bodyStart)
      if bodyEnd < 0 then
        Console.err.println(s"docsync: unterminated block for $name")
        sys.exit(2)

      val embedded = text.substring(bodyStart, bodyEnd)
      val actual = Files.readString(file).stripTrailing()
      if embedded == actual then println(s"ok       $name")
      else
        drifted = name :: drifted
        val embeddedLines = embedded.linesIterator.size
        val actualLines = actual.linesIterator.size
        println(s"drifted  $name  ($embeddedLines -> $actualLines lines)")
        if !check then text = text.substring(0, bodyStart) + actual + text.substring(bodyEnd)

    if matched == 0 then
      Console.err.println("docsync: found no embedded source blocks -- has the heading format changed?")
      sys.exit(2)

    if drifted.isEmpty then println(s"\n$matched block(s) embedded, all in sync")
    else if check then
      println(s"\n${drifted.size} block(s) out of sync: ${drifted.mkString(", ")}")
      println("run without --check to rewrite them")
      sys.exit(1)
    else
      Files.writeString(doc, text)
      println(s"\nrewrote ${drifted.size} block(s) in $doc")
