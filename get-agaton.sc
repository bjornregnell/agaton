// get-agaton.sc — build agaton from source and install it.
//
//   scala-cli run get-agaton.sc                    # native build -> ~/.local/bin, seed ~/.config/agaton
//   scala-cli run get-agaton.sc -- --dry-run       # print every step, change nothing
//   scala-cli run get-agaton.sc -- --jvm           # skip the native image, install a jar + launcher
//   scala-cli run get-agaton.sc -- --prefix ~/bin --home ~/sync/agaton
//
// Run it from a clone (it uses the sources in the current directory), or from
// anywhere else, in which case it clones the repository into a temporary
// directory first.
//
// ⚠ READ THIS SCRIPT BEFORE RUNNING IT. It is deliberately one short file with
// NO dependencies — no `//> using dep`, nothing to resolve but a compiler — so
// that the one artifact whose job is to be trustworthy can be read in a couple
// of minutes. It does exactly four things: builds a binary with scala-cli,
// copies it onto your PATH, creates a config directory, and copies starter
// profiles into it IF no registry is there already. It never overwrites an
// existing profiles.json, never uses sudo, and touches nothing else.
//
// Scala rather than shell on purpose: building already requires scala-cli, so
// this costs no extra dependency, and it works on Windows where a .sh does not.
//
// There is no release binary to download yet. When there is, the download path
// belongs in this same file.

//> using jvm 21

import java.nio.file.{Files, Path, Paths, StandardCopyOption}
import scala.jdk.CollectionConverters.*

val RepoUrl = "https://github.com/bjornregnell/agaton.git"

def home: Path = Paths.get(sys.props("user.home"))

def expand(s: String): Path =
  if s == "~" then home
  else if s.startsWith("~/") then home.resolve(s.drop(2))
  else Paths.get(s).toAbsolutePath

def die(msg: String): Nothing =
  System.err.println(s"get-agaton: $msg")
  sys.exit(2)

def say(s: String): Unit = println(s)

// ---- arguments -----------------------------------------------------------

val argv = args.toList

def flagValue(name: String): Option[String] =
  argv.sliding(2).collectFirst { case `name` :: v :: _ => v }
    .orElse(if argv.contains(name) then die(s"$name needs a directory") else None)

if argv.contains("--help") || argv.contains("-h") then
  say("usage: scala-cli run get-agaton.sc -- [--dry-run] [--jvm] [--prefix DIR] [--home DIR]")
  sys.exit(0)

val dryRun = argv.contains("--dry-run")
val native = !argv.contains("--jvm")
val prefix = flagValue("--prefix").map(expand).getOrElse(home.resolve(".local").resolve("bin"))
val configHome = flagValue("--home").map(expand).getOrElse:
  sys.env.get("XDG_CONFIG_HOME").filter(_.nonEmpty).map(expand)
    .getOrElse(home.resolve(".config")).resolve("agaton")

val known = Set("--dry-run", "--jvm", "--help", "-h", "--prefix", "--home")
argv.find(a => a.startsWith("--") && !known(a)).foreach(a => die(s"unknown argument '$a' (try --help)"))

// ---- running other programs ---------------------------------------------

/** Run a command with its output attached to this terminal; return its exit code. */
def exec(cmd: Seq[String], cwd: Option[Path] = None): Int =
  val pb = new ProcessBuilder(cmd.asJava).inheritIO()
  cwd.foreach(d => pb.directory(d.toFile))
  pb.start().waitFor()

def execOrDie(cmd: Seq[String], what: String, cwd: Option[Path] = None): Unit =
  if dryRun then say(s"   would: ${cmd.mkString(" ")}")
  else if exec(cmd, cwd) != 0 then die(s"$what failed")

def onPath(program: String): Boolean =
  try
    val pb = new ProcessBuilder(Seq(program, "--version").asJava)
    pb.redirectOutput(ProcessBuilder.Redirect.DISCARD)
    pb.redirectError(ProcessBuilder.Redirect.DISCARD)
    pb.start().waitFor() == 0
  catch case _: Throwable => false

// ---- 1. sources ----------------------------------------------------------

if !onPath("scala-cli") then
  die("scala-cli is required to build from source — https://scala-cli.virtuslab.org/install")

val cwd = Paths.get("").toAbsolutePath
val src =
  if Files.isDirectory(cwd.resolve("scala")) then
    say(s"sources    $cwd  (this clone)")
    cwd
  else
    val tmp = Files.createTempDirectory("agaton-").resolve("agaton")
    say(s"sources    cloning $RepoUrl")
    execOrDie(Seq("git", "clone", "--depth", "1", "--quiet", RepoUrl, tmp.toString), "git clone")
    tmp

say(s"binary     ${prefix.resolve("agaton")}   (${if native then "native" else "jvm"} build)")
say(s"config     $configHome")
say("")

// ---- 2. build ------------------------------------------------------------

val built = src.resolve("bin").resolve("agaton")
val jar = src.resolve("bin").resolve("agaton.jar")

if native then
  say("building native image (about 30 s, longer the first time)...")
  execOrDie(
    Seq("scala-cli", "--power", "package", src.resolve("scala").toString,
      "-o", built.toString, "--native-image", "-f", "--main-class", "agaton.AgatonMain"),
    "native build")
else
  say("building runnable jar...")
  execOrDie(
    Seq("scala-cli", "--power", "package", src.resolve("scala").toString,
      "-o", jar.toString, "--assembly", "-f", "--main-class", "agaton.AgatonMain"),
    "jar build")

// ---- 3. install the binary ----------------------------------------------

def copy(from: Path, to: Path): Unit =
  if dryRun then say(s"   would: copy $from -> $to")
  else
    Files.createDirectories(to.getParent)
    Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING)
    to.toFile.setExecutable(true)

if Files.exists(prefix.resolve("agaton")) then
  say(s"note       replacing the existing ${prefix.resolve("agaton")}")

if native then copy(built, prefix.resolve("agaton"))
else
  copy(jar, prefix.resolve("agaton.jar"))
  val launcher = prefix.resolve("agaton")
  if dryRun then say(s"   would: write a launcher at $launcher")
  else if sys.props("os.name").toLowerCase.contains("win") then
    say(s"note       on Windows, run it as: java -jar ${prefix.resolve("agaton.jar")}")
  else
    Files.createDirectories(prefix)
    Files.writeString(launcher,
      s"""|#!/usr/bin/env bash
          |exec java -jar "${prefix.resolve("agaton.jar")}" "$$@"
          |""".stripMargin)
    launcher.toFile.setExecutable(true)

// ---- 4. seed the config home, never overwriting a registry --------------

val registry = configHome.resolve("profiles.json")
if Files.exists(registry) then
  say(s"config     $registry exists — left untouched")
else
  say(s"config     seeding starter profiles into $configHome")
  for f <- Seq("profiles.json", "shared.json", "work.json", "personal.json") do
    val from = src.resolve("config").resolve(f)
    if Files.exists(from) then
      if dryRun then say(s"   would: copy $f")
      else
        Files.createDirectories(configHome)
        Files.copy(from, configHome.resolve(f), StandardCopyOption.REPLACE_EXISTING)
  say("           EDIT THESE: an example with two profiles, 'work' on the stock")
  say("           layout and 'personal' on ~/.claude-personal.")

say("")
if dryRun then
  say("dry run — nothing was changed.")
else
  val pathDirs = sys.env.getOrElse("PATH", "").split(java.io.File.pathSeparatorChar).toSet
  if !pathDirs.contains(prefix.toString) then
    say(s"note       $prefix is not on your PATH — add it to your shell profile")

  val ok = exec(Seq(prefix.resolve("agaton").toString, "list")) == 0
  say("")
  say(if ok then "installed  ok — the profiles above are what it found"
      else s"installed  the binary is in place, but 'agaton list' failed — check $registry")
  say("")
  say("next:  agaton doctor      # what still needs doing")
  say("       agaton run work    # launch one")
