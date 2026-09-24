package agaton

import java.nio.file.Files

/** agaton — run one coding-agent account, with its own configuration home.
  *
  *   agaton run work                 # launch the work profile
  *   agaton work                     # shorthand for the same
  *   agaton run personal -- -p "hi"  # pass arguments through to the provider
  *   agaton who                      # which account each profile holds
  *   agaton list                     # profiles and where they live
  *   agaton show personal            # the settings that will be applied
  *   agaton doctor                   # check the setup
  */
object AgatonMain:

  private def usage = """usage: agaton run <profile> [-- <provider args>...]
       agaton <profile> [-- <provider args>...]     (shorthand for run)
       agaton who [<profile>] | list | show <profile> | doctor

profiles come from $AGATON_HOME/profiles.json
(default: ${XDG_CONFIG_HOME:-~/.config}/agaton)"""

  private def die(msg: String): Nothing =
    Console.err.println(s"agaton: $msg")
    sys.exit(2)

  /** Strip a leading `--` so both `agaton run work -- -p hi` and
    * `agaton run work -p hi` pass the same arguments through. */
  private def passthrough(rest: List[String]): List[String] = rest match
    case "--" :: tail => tail
    case other        => other

  private def launch(registry: Registry, name: String, rest: List[String]): Nothing =
    val p = registry.find(name).fold(die, identity)
    val settings = Launcher.effectiveSettings(p, registry).fold(die, identity)
    sys.exit(Launcher.run(p, settings, passthrough(rest)))

  def main(args: Array[String]): Unit =
    args.toList match
      case Nil | ("-h" | "--help" | "help") :: _ =>
        println(usage)

      case cmd :: rest =>
        val registry = Registry.load().fold(die, identity)
        cmd match
          case "run" =>
            rest match
              case name :: tail => launch(registry, name, tail)
              case Nil          => die("run needs a profile name")

          case "who" =>
            Who.report(registry, rest.headOption).fold(die, identity)

          case "list" =>
            val width = registry.profiles.map(_.name.length).maxOption.getOrElse(0)
            for p <- registry.profiles do
              val signedIn = Account.of(p).fold(err => s"($err)", a => s"${a.email} — ${a.org}")
              val home =
                if p.isStock then s"${p.home}  (stock layout, CLAUDE_CONFIG_DIR unset)"
                else p.home.toString
              println(s"${p.name.padTo(width, ' ')}  $home")
              println(s"${" " * width}  $signedIn")
              if p.description.nonEmpty then println(s"${" " * width}  ${p.description}")

          case "show" =>
            val name = rest.headOption.getOrElse(die("show needs a profile name"))
            val p = registry.find(name).fold(die, identity)
            val merged = Merge.all(registry.shared ++ p.overlays).fold(die, identity)
            println(ujson.write(merged, indent = 2))

          case "doctor" =>
            var problems = 0
            def check(ok: Boolean, label: String, detail: String = ""): Unit =
              if !ok then problems += 1
              val hint = if ok || detail.isEmpty then "" else s" — $detail"
              println(s"${if ok then "ok  " else "FAIL"}  $label$hint")

            check(Files.isDirectory(Root.dir), s"registry home ${Root.dir}",
              "set AGATON_HOME, or create the default")
            for p <- registry.profiles do
              Launcher.binaryOf(p) match
                case Left(err) => check(false, s"${p.name}: provider ${p.provider}", err)
                case Right(bin) =>
                  val onPath = Runtime.getRuntime.exec(Array("which", bin)).waitFor() == 0
                  check(onPath, s"${p.name}: $bin on PATH")
              check(Files.isDirectory(p.home), s"${p.name}: config home ${p.home}",
                "create it, or run the profile once")
              check(Files.exists(p.home.resolve(".credentials.json")),
                s"${p.name}: signed in", "run it once and use /login")
              check(Merge.all(registry.shared ++ p.overlays).isRight, s"${p.name}: settings parse")
            sys.exit(if problems == 0 then 0 else 1)

          // Shorthand: a bare profile name means `run`. Reserved names are
          // refused at registry load, so this cannot shadow a subcommand.
          case name => launch(registry, name, rest)
